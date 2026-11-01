package com.example.iss.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SingleFlight<T>: concurrent callers for the SAME key share one upstream execution.
 * All synchronization below uses CountDownLatch (no Thread.sleep): a "started" latch
 * confirms the leader has entered its work callable and is now blocked before any
 * follower thread is started, and each follower's own "started" latch confirms it has
 * reached the call site before the test releases the leader's work gate. This removes
 * timing flakiness without ever sleeping.
 */
class SingleFlightTest {

    private final SingleFlight<String> singleFlight = new SingleFlight<>();

    @Nested
    @DisplayName("concurrent calls for the same key")
    class ConcurrentCallsSameKey {

        @Test
        @DisplayName("given concurrent callers for the same key, when executed, then the work runs exactly once and all callers share the result")
        void givenConcurrentCallersSameKey_whenExecuted_thenWorkRunsOnceAndAllShareResult() throws Exception {
            AtomicInteger callCount = new AtomicInteger();
            CountDownLatch leaderStarted = new CountDownLatch(1);
            CountDownLatch releaseWork = new CountDownLatch(1);

            Callable<String> leaderWork = () -> {
                callCount.incrementAndGet();
                leaderStarted.countDown();
                releaseWork.await();
                return "shared-result";
            };
            Callable<String> followerWorkShouldNeverRun = () -> {
                callCount.incrementAndGet();
                return "should-not-run";
            };

            AtomicReference<String> leaderResult = new AtomicReference<>();
            Thread leader = new Thread(() -> {
                try {
                    leaderResult.set(singleFlight.execute("key", leaderWork));
                } catch (Exception ignored) {
                }
            });
            leader.start();
            assertThat(leaderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            int followerCount = 5;
            CountDownLatch followersStarted = new CountDownLatch(followerCount);
            Thread[] followers = new Thread[followerCount];
            String[] followerResults = new String[followerCount];
            for (int i = 0; i < followerCount; i++) {
                int idx = i;
                followers[i] = new Thread(() -> {
                    followersStarted.countDown();
                    try {
                        followerResults[idx] = singleFlight.execute("key", followerWorkShouldNeverRun);
                    } catch (Exception ignored) {
                    }
                });
                followers[i].start();
            }
            assertThat(followersStarted.await(5, TimeUnit.SECONDS)).isTrue();

            releaseWork.countDown();
            leader.join(5000);
            for (Thread f : followers) {
                f.join(5000);
            }

            assertThat(callCount.get()).isEqualTo(1);
            assertThat(leaderResult.get()).isEqualTo("shared-result");
            for (String result : followerResults) {
                assertThat(result).isEqualTo("shared-result");
            }
        }

        @Test
        @DisplayName("given the leader's work throws, when followers resolve, then all callers receive an equivalent failure")
        void givenLeaderWorkThrows_whenFollowersResolve_thenAllCallersReceiveEquivalentFailure() throws Exception {
            CountDownLatch leaderStarted = new CountDownLatch(1);
            CountDownLatch releaseWork = new CountDownLatch(1);
            RuntimeException boom = new RuntimeException("boom");

            Callable<String> leaderWork = () -> {
                leaderStarted.countDown();
                releaseWork.await();
                throw boom;
            };

            AtomicReference<Exception> leaderError = new AtomicReference<>();
            Thread leader = new Thread(() -> {
                try {
                    singleFlight.execute("key", leaderWork);
                } catch (Exception e) {
                    leaderError.set(e);
                }
            });
            leader.start();
            assertThat(leaderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch followerStarted = new CountDownLatch(1);
            AtomicReference<Exception> followerError = new AtomicReference<>();
            Thread follower = new Thread(() -> {
                followerStarted.countDown();
                try {
                    singleFlight.execute("key", leaderWork);
                } catch (Exception e) {
                    followerError.set(e);
                }
            });
            follower.start();
            assertThat(followerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            releaseWork.countDown();
            leader.join(5000);
            follower.join(5000);

            assertThat(leaderError.get()).isSameAs(boom);
            assertThat(followerError.get()).isNotNull();
            assertThat(followerError.get().getCause()).isSameAs(boom);
        }

        @Test
        @DisplayName("given calls for different keys, when executed concurrently, then each key's work runs independently")
        void givenCallsForDifferentKeys_whenExecutedConcurrently_thenEachKeyRunsIndependently() throws Exception {
            AtomicInteger keyACalls = new AtomicInteger();
            AtomicInteger keyBCalls = new AtomicInteger();

            Callable<String> workA = () -> {
                keyACalls.incrementAndGet();
                return "a-result";
            };
            Callable<String> workB = () -> {
                keyBCalls.incrementAndGet();
                return "b-result";
            };

            String resultA = singleFlight.execute("key-a", workA);
            String resultB = singleFlight.execute("key-b", workB);

            assertThat(resultA).isEqualTo("a-result");
            assertThat(resultB).isEqualTo("b-result");
            assertThat(keyACalls.get()).isEqualTo(1);
            assertThat(keyBCalls.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("sequential calls for the same key")
    class SequentialCallsSameKey {

        @Test
        @DisplayName("given a call has completed, when a new sequential call for the same key is made, then it triggers a fresh execution")
        void givenCallCompleted_whenNewSequentialCallMade_thenTriggersFreshExecution() throws Exception {
            AtomicInteger callCount = new AtomicInteger();
            Callable<String> work = () -> "result-" + callCount.incrementAndGet();

            String first = singleFlight.execute("key", work);
            String second = singleFlight.execute("key", work);

            assertThat(first).isEqualTo("result-1");
            assertThat(second).isEqualTo("result-2");
            assertThat(callCount.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("given the work throws, when the call completes, then the in-flight entry is removed so the next call for that key retries")
        void givenWorkThrows_whenCallCompletes_thenEntryRemovedSoNextCallRetries() throws Exception {
            AtomicInteger attempts = new AtomicInteger();
            Callable<String> failingThenSucceeding = () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("first attempt fails");
                }
                return "second-attempt-result";
            };

            assertThatThrownBy(() -> singleFlight.execute("key", failingThenSucceeding))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("first attempt fails");

            String secondResult = singleFlight.execute("key", failingThenSucceeding);

            assertThat(secondResult).isEqualTo("second-attempt-result");
            assertThat(attempts.get()).isEqualTo(2);
        }
    }
}
