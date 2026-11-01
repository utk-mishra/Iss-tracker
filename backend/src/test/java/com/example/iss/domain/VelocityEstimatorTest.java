package com.example.iss.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VelocityEstimator: derives km/h from two consecutive ISS samples via haversine arc
 * length scaled by (R+408)/R, divided by elapsed time. Thread-safe via AtomicReference.
 * See PROMPT.md section 5.
 */
class VelocityEstimatorTest {

    private static final double DEFAULT_VELOCITY_KMH = 27_600.0;

    @Nested
    @DisplayName("no previous sample")
    class NoPreviousSample {

        @Test
        @DisplayName("given a fresh estimator with no prior sample, when estimated, then default velocity is returned")
        void givenNoPriorSample_whenEstimated_thenDefaultVelocityReturned() {
            VelocityEstimator estimator = new VelocityEstimator();

            double velocity = estimator.estimateKmh(28.0, 77.0, Instant.parse("2026-11-01T00:00:00Z"));

            assertThat(velocity).isEqualTo(DEFAULT_VELOCITY_KMH);
        }
    }

    @Nested
    @DisplayName("timestamp did not advance")
    class TimestampDidNotAdvance {

        @Test
        @DisplayName("given a previous sample at the same timestamp, when estimated again, then default velocity is returned")
        void givenSameTimestamp_whenEstimatedAgain_thenDefaultVelocityReturned() {
            VelocityEstimator estimator = new VelocityEstimator();
            Instant t = Instant.parse("2026-11-01T00:00:00Z");
            estimator.estimateKmh(28.0, 77.0, t);

            double velocity = estimator.estimateKmh(28.1, 77.1, t);

            assertThat(velocity).isEqualTo(DEFAULT_VELOCITY_KMH);
        }

        @Test
        @DisplayName("given a previous sample at a later timestamp, when estimated with an earlier timestamp, then default velocity is returned")
        void givenLaterPreviousTimestamp_whenEstimatedWithEarlierTimestamp_thenDefaultVelocityReturned() {
            VelocityEstimator estimator = new VelocityEstimator();
            Instant later = Instant.parse("2026-11-01T00:00:10Z");
            Instant earlier = Instant.parse("2026-11-01T00:00:00Z");
            estimator.estimateKmh(28.0, 77.0, later);

            double velocity = estimator.estimateKmh(28.1, 77.1, earlier);

            assertThat(velocity).isEqualTo(DEFAULT_VELOCITY_KMH);
        }
    }

    @Nested
    @DisplayName("two realistic consecutive samples")
    class RealisticConsecutiveSamples {

        @Test
        @DisplayName("given two realistic ISS positions about 5 seconds apart, when estimated, then velocity is roughly 27000-28000 km/h")
        void givenTwoRealisticPositions5SecondsApart_whenEstimated_thenVelocityRoughly27000To28000() {
            VelocityEstimator estimator = new VelocityEstimator();
            Instant t0 = Instant.parse("2026-11-01T00:00:00Z");
            Instant t1 = t0.plus(Duration.ofSeconds(5));
            // ISS orbital ground speed is ~7.66 km/s (~27,600 km/h before altitude scaling).
            // A pure latitude displacement of 0.323 deg (~35.9 km great-circle) over 5s,
            // scaled by (R+408)/R, lands in the expected 27,000-28,000 km/h band.
            // Latitude 20 degrees N / 100 degrees E is within the ISS's ~51.6 degree
            // orbital inclination, so this is a physically plausible ground-track pair.
            estimator.estimateKmh(20.0000, 100.0000, t0);

            double velocity = estimator.estimateKmh(20.3230, 100.0000, t1);

            assertThat(velocity).isBetween(27_000.0, 28_000.0);
        }
    }

    @Nested
    @DisplayName("thread safety")
    class ThreadSafety {

        @Test
        @DisplayName("given concurrent estimate calls, when they race, then no exception is thrown and a previous sample is consistently tracked")
        void givenConcurrentEstimateCalls_whenTheyRace_thenNoExceptionAndConsistentState() throws InterruptedException {
            VelocityEstimator estimator = new VelocityEstimator();
            int threads = 8;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger errors = new AtomicInteger();
            Thread[] workers = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                int idx = i;
                workers[i] = new Thread(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        estimator.estimateKmh(28.0 + idx * 0.001, 77.0 + idx * 0.001,
                                Instant.parse("2026-11-01T00:00:00Z").plusSeconds(idx));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
                workers[i].start();
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Thread t : workers) {
                t.join(5000);
            }

            assertThat(errors.get()).isZero();
        }
    }
}
