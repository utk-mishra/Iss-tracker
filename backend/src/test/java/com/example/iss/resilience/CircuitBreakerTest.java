package com.example.iss.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CircuitBreaker: one per upstream. 3 failures -> OPEN; skips upstream calls while OPEN;
 * after the cooldown it goes HALF_OPEN and allows exactly one trial call.
 * See PROMPT.md section 2 and section 9.
 */
class CircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private MutableClock clock;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        breaker = new CircuitBreaker(clock, FAILURE_THRESHOLD, COOLDOWN);
    }

    @Nested
    @DisplayName("closed state")
    class Closed {

        @Test
        @DisplayName("given a new breaker, when checked, then it allows calls and is CLOSED")
        void givenNewBreaker_whenChecked_thenAllowsCallsAndIsClosed() {
            assertThat(breaker.allowRequest()).isTrue();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("given fewer than threshold failures, when checked, then it stays CLOSED")
        void givenFewerThanThresholdFailures_whenChecked_thenStaysClosed() {
            breaker.recordFailure();
            breaker.recordFailure();

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("given a success after some failures, when recorded, then failure count resets")
        void givenSuccessAfterFailures_whenRecorded_thenFailureCountResets() {
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordSuccess();
            breaker.recordFailure();
            breaker.recordFailure();

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.allowRequest()).isTrue();
        }
    }

    @Nested
    @DisplayName("opens after threshold failures")
    class OpensAfterThreshold {

        @Test
        @DisplayName("given exactly 3 consecutive failures, when checked, then breaker opens")
        void givenExactly3ConsecutiveFailures_whenChecked_thenBreakerOpens() {
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordFailure();

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("given breaker is OPEN, when a request is attempted, then it is skipped (not allowed)")
        void givenBreakerOpen_whenRequestAttempted_thenSkipped() {
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordFailure();

            assertThat(breaker.allowRequest()).isFalse();
        }
    }

    @Nested
    @DisplayName("half-open after cooldown")
    class HalfOpenAfterCooldown {

        @BeforeEach
        void openTheBreaker() {
            breaker.recordFailure();
            breaker.recordFailure();
            breaker.recordFailure();
        }

        @Test
        @DisplayName("given breaker OPEN and cooldown not yet elapsed, when checked, then still not allowed")
        void givenOpenAndCooldownNotElapsed_whenChecked_thenStillNotAllowed() {
            clock.advance(COOLDOWN.minusSeconds(1));

            assertThat(breaker.allowRequest()).isFalse();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("given breaker OPEN and cooldown elapsed, when checked, then it transitions to HALF_OPEN and allows one trial")
        void givenOpenAndCooldownElapsed_whenChecked_thenHalfOpenAllowsOneTrial() {
            clock.advance(COOLDOWN);

            boolean allowed = breaker.allowRequest();

            assertThat(allowed).isTrue();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("given HALF_OPEN trial succeeds, when recorded, then breaker closes")
        void givenHalfOpenTrialSucceeds_whenRecorded_thenBreakerCloses() {
            clock.advance(COOLDOWN);
            breaker.allowRequest();

            breaker.recordSuccess();

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("given HALF_OPEN trial fails, when recorded, then breaker re-opens and restarts cooldown")
        void givenHalfOpenTrialFails_whenRecorded_thenBreakerReopensAndRestartsCooldown() {
            clock.advance(COOLDOWN);
            breaker.allowRequest();

            breaker.recordFailure();

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(breaker.allowRequest()).isFalse();

            clock.advance(COOLDOWN.minusSeconds(1));
            assertThat(breaker.allowRequest()).isFalse();

            clock.advance(Duration.ofSeconds(1));
            assertThat(breaker.allowRequest()).isTrue();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("thread safety")
    class ThreadSafety {

        @Test
        @DisplayName("given concurrent failure recordings, when threshold is reached, then breaker opens exactly once and consistently")
        void givenConcurrentFailures_whenThresholdReached_thenBreakerOpensConsistently() throws InterruptedException {
            int threads = 10;
            Thread[] workers = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                workers[i] = new Thread(breaker::recordFailure);
            }
            for (Thread t : workers) {
                t.start();
            }
            for (Thread t : workers) {
                t.join();
            }

            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(breaker.allowRequest()).isFalse();
        }
    }
}
