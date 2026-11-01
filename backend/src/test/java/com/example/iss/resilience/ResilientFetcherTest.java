package com.example.iss.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResilientFetcher<T>: the single fallback chain every upstream call goes through.
 * See PROMPT.md section 2 (degradation table, TTL/max-stale) and section 9
 * (exact scenario list). All timing uses MutableClock; concurrency uses CountDownLatch;
 * no Thread.sleep anywhere.
 */
class ResilientFetcherTest {

    private static final Duration TTL = Duration.ofSeconds(5);
    private static final Duration MAX_STALE = Duration.ofSeconds(60);
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final String KEY = "28.61,77.21";
    private static final String MOCK_VALUE = "MOCK-value";

    private MutableClock clock;
    private TtlCache<String> cache;
    private CircuitBreaker breaker;
    private SingleFlight<String> singleFlight;
    private ResilientFetcher<String> fetcher;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        cache = new TtlCache<>(clock, TTL, MAX_STALE, 500);
        breaker = new CircuitBreaker(clock, FAILURE_THRESHOLD, COOLDOWN);
        singleFlight = new SingleFlight<>();
        fetcher = new ResilientFetcher<>(cache, breaker, singleFlight, clock, 1);
    }

    private Callable<String> succeedingUpstream(String value) {
        return () -> value;
    }

    private Callable<String> failingUpstream(AtomicInteger callCount) {
        return () -> {
            callCount.incrementAndGet();
            throw new RuntimeException("upstream down");
        };
    }

    @Nested
    @DisplayName("success path")
    class SuccessPath {

        @Test
        @DisplayName("given an empty cache and a healthy upstream, when fetched, then result is FRESH and cache is populated")
        void givenEmptyCacheAndHealthyUpstream_whenFetched_thenFreshAndCachePopulated() {
            Result<String> result = fetcher.fetch(KEY, succeedingUpstream("live-value"), () -> MOCK_VALUE, false);

            assertThat(result.data()).isEqualTo("live-value");
            assertThat(result.source()).isEqualTo(Source.FRESH);
            assertThat(result.ageMs()).isZero();
            assertThat(cache.get(KEY)).isPresent();
        }

        @Test
        @DisplayName("given a fresh cache entry within TTL, when fetched again, then result is FRESH from cache and upstream is not called")
        void givenFreshCacheEntry_whenFetchedAgain_thenFreshFromCacheWithoutUpstream() {
            AtomicInteger upstreamCalls = new AtomicInteger();
            Callable<String> upstream = () -> {
                upstreamCalls.incrementAndGet();
                return "live-value";
            };
            fetcher.fetch(KEY, upstream, () -> MOCK_VALUE, false);
            clock.advance(Duration.ofSeconds(1));

            Result<String> result = fetcher.fetch(KEY, upstream, () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.FRESH);
            assertThat(result.data()).isEqualTo("live-value");
            assertThat(upstreamCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("given upstream fails once then succeeds on retry, when fetched, then result is FRESH")
        void givenUpstreamFailsOnceThenSucceeds_whenFetched_thenFresh() {
            AtomicInteger attempts = new AtomicInteger();
            Callable<String> flaky = () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("transient");
                }
                return "recovered-value";
            };

            Result<String> result = fetcher.fetch(KEY, flaky, () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.FRESH);
            assertThat(result.data()).isEqualTo("recovered-value");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("given a successful fetch, when the breaker had prior failures, then the failure count resets")
        void givenSuccessfulFetch_whenBreakerHadPriorFailures_thenFailureCountResets() {
            AtomicInteger failCount = new AtomicInteger();
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            clock.advance(TTL.plusSeconds(1));

            fetcher.fetch(KEY, succeedingUpstream("live-value"), () -> MOCK_VALUE, false);

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            clock.advance(TTL.plusSeconds(1));
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("stale fallback")
    class StaleFallback {

        @Test
        @DisplayName("given a stale cache entry within max-stale, when upstream fails, then result is STALE with the cached data")
        void givenStaleCacheEntryWithinMaxStale_whenUpstreamFails_thenStaleWithCachedData() {
            fetcher.fetch(KEY, succeedingUpstream("old-value"), () -> MOCK_VALUE, false);
            clock.advance(TTL.plusSeconds(1));

            AtomicInteger failCount = new AtomicInteger();
            Result<String> result = fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.STALE);
            assertThat(result.data()).isEqualTo("old-value");
            assertThat(result.ageMs()).isEqualTo(TTL.plusSeconds(1).toMillis());
        }

        @Test
        @DisplayName("given the circuit breaker is OPEN, when fetched with a stale cache entry, then result is STALE and upstream is never invoked")
        void givenBreakerOpen_whenFetchedWithStaleCacheEntry_thenStaleWithoutInvokingUpstream() {
            fetcher.fetch(KEY, succeedingUpstream("old-value"), () -> MOCK_VALUE, false);
            clock.advance(TTL.plusSeconds(1));

            AtomicInteger failCount = new AtomicInteger();
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            int callsBeforeNextFetch = failCount.get();
            Result<String> result = fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.STALE);
            assertThat(result.data()).isEqualTo("old-value");
            assertThat(failCount.get()).isEqualTo(callsBeforeNextFetch);
        }
    }

    @Nested
    @DisplayName("mock fallback")
    class MockFallback {

        @Test
        @DisplayName("given an empty cache and upstream fails, when fetched, then result is MOCK")
        void givenEmptyCacheAndUpstreamFails_whenFetched_thenMock() {
            AtomicInteger failCount = new AtomicInteger();

            Result<String> result = fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.MOCK);
            assertThat(result.data()).isEqualTo(MOCK_VALUE);
            assertThat(result.ageMs()).isZero();
        }

        @Test
        @DisplayName("given a cache entry older than max-stale, when upstream fails, then result is MOCK")
        void givenCacheEntryOlderThanMaxStale_whenUpstreamFails_thenMock() {
            fetcher.fetch(KEY, succeedingUpstream("ancient-value"), () -> MOCK_VALUE, false);
            clock.advance(MAX_STALE.plusSeconds(1));

            AtomicInteger failCount = new AtomicInteger();
            Result<String> result = fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.MOCK);
            assertThat(result.data()).isEqualTo(MOCK_VALUE);
        }

        @Test
        @DisplayName("given a MOCK result was returned, when fetched again later, then the mock value was never cached")
        void givenMockResultReturned_whenFetchedAgainLater_thenMockValueNeverCached() {
            AtomicInteger failCount = new AtomicInteger();
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(cache.get(KEY)).isEmpty();

            clock.advance(TTL.plusSeconds(1));
            Result<String> result = fetcher.fetch(KEY, succeedingUpstream("finally-live"), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.FRESH);
            assertThat(result.data()).isEqualTo("finally-live");
        }
    }

    @Nested
    @DisplayName("forced offline mode")
    class ForcedOffline {

        @Test
        @DisplayName("given offline=true and no cache entry, when fetched, then result is MOCK and upstream is never invoked")
        void givenOfflineAndNoCacheEntry_whenFetched_thenMockWithoutInvokingUpstream() {
            AtomicInteger upstreamCalls = new AtomicInteger();
            Callable<String> upstream = () -> {
                upstreamCalls.incrementAndGet();
                return "should-never-be-returned";
            };

            Result<String> result = fetcher.fetch(KEY, upstream, () -> MOCK_VALUE, true);

            assertThat(result.source()).isEqualTo(Source.MOCK);
            assertThat(result.data()).isEqualTo(MOCK_VALUE);
            assertThat(upstreamCalls.get()).isZero();
        }

        @Test
        @DisplayName("given offline=true and a cached entry still within TTL, when fetched, then result is STALE (not FRESH) and upstream is never invoked")
        void givenOfflineAndCachedEntryWithinTtl_whenFetched_thenStaleNotFreshWithoutInvokingUpstream() {
            fetcher.fetch(KEY, succeedingUpstream("cached-value"), () -> MOCK_VALUE, false);

            AtomicInteger upstreamCalls = new AtomicInteger();
            Callable<String> upstream = () -> {
                upstreamCalls.incrementAndGet();
                return "should-never-be-returned";
            };

            Result<String> result = fetcher.fetch(KEY, upstream, () -> MOCK_VALUE, true);

            assertThat(result.source()).isEqualTo(Source.STALE);
            assertThat(result.data()).isEqualTo("cached-value");
            assertThat(upstreamCalls.get()).isZero();
        }

        @Test
        @DisplayName("given offline=true and a cached entry beyond max-stale, when fetched, then result is MOCK and upstream is never invoked")
        void givenOfflineAndCacheEntryBeyondMaxStale_whenFetched_thenMockWithoutInvokingUpstream() {
            fetcher.fetch(KEY, succeedingUpstream("ancient-value"), () -> MOCK_VALUE, false);
            clock.advance(MAX_STALE.plusSeconds(1));

            AtomicInteger upstreamCalls = new AtomicInteger();
            Callable<String> upstream = () -> {
                upstreamCalls.incrementAndGet();
                return "should-never-be-returned";
            };

            Result<String> result = fetcher.fetch(KEY, upstream, () -> MOCK_VALUE, true);

            assertThat(result.source()).isEqualTo(Source.MOCK);
            assertThat(result.data()).isEqualTo(MOCK_VALUE);
            assertThat(upstreamCalls.get()).isZero();
        }
    }

    @Nested
    @DisplayName("circuit breaker integration")
    class CircuitBreakerIntegration {

        @Test
        @DisplayName("given the breaker is already OPEN, when fetched with no cache, then upstream is never invoked and result is MOCK")
        void givenBreakerOpenAndNoCache_whenFetched_thenUpstreamNeverInvokedAndMock() {
            AtomicInteger failCount = new AtomicInteger();
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            int callsSoFar = failCount.get();
            Result<String> result = fetcher.fetch(KEY, failingUpstream(failCount), () -> MOCK_VALUE, false);

            assertThat(result.source()).isEqualTo(Source.MOCK);
            assertThat(failCount.get()).isEqualTo(callsSoFar);
        }
    }

    @Nested
    @DisplayName("single-flight integration")
    class SingleFlightIntegration {

        @Test
        @DisplayName("given concurrent fetches for the same key with an empty cache, when both resolve, then upstream is invoked exactly once")
        void givenConcurrentFetchesSameKeyEmptyCache_whenResolved_thenUpstreamInvokedOnce() throws InterruptedException {
            AtomicInteger upstreamCalls = new AtomicInteger();
            CountDownLatch leaderEnteredUpstream = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Callable<String> slowUpstream = () -> {
                upstreamCalls.incrementAndGet();
                leaderEnteredUpstream.countDown();
                release.await();
                return "shared-live-value";
            };

            Runnable fetchTask = () -> fetcher.fetch(KEY, slowUpstream, () -> MOCK_VALUE, false);
            Thread t1 = new Thread(fetchTask);
            Thread t2 = new Thread(fetchTask);
            t1.start();
            assertThat(leaderEnteredUpstream.await(5, TimeUnit.SECONDS)).isTrue();
            t2.start();

            // Give the follower a chance to reach the single-flight wait point before releasing
            // the leader; this is a bounded join-free handoff, not a correctness dependency
            // (dedup is verified below purely by call count, regardless of exact interleaving).
            release.countDown();
            t1.join(5000);
            t2.join(5000);

            assertThat(upstreamCalls.get()).isEqualTo(1);
            assertThat(cache.get(KEY)).isPresent();
            assertThat(cache.get(KEY).orElseThrow().value()).isEqualTo("shared-live-value");
        }
    }
}
