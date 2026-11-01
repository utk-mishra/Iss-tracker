package com.example.iss.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Hand-written circuit breaker, one instance per upstream (PROMPT.md section 2).
 *
 * <p>States:
 * <ul>
 *   <li>CLOSED: requests allowed; after {@code failureThreshold} consecutive failures
 *       (no intervening success) it opens.</li>
 *   <li>OPEN: requests are skipped until {@code cooldown} has elapsed since the breaker
 *       opened.</li>
 *   <li>HALF_OPEN: exactly one trial request is allowed after the cooldown; a success
 *       closes the breaker and resets the failure count, a failure re-opens it and
 *       restarts the cooldown.</li>
 * </ul>
 *
 * <p>Thread-safe: all state transitions are synchronized, which is appropriate given the
 * low call volume per upstream (at most one breaker per of three upstreams).
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final Clock clock;
    private final int failureThreshold;
    private final Duration cooldown;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt;
    private boolean halfOpenTrialInFlight = false;

    public CircuitBreaker(Clock clock, int failureThreshold, Duration cooldown) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Returns whether a call to the upstream is currently permitted. When OPEN and the
     * cooldown has elapsed, this transitions the breaker to HALF_OPEN and allows exactly
     * one trial call; subsequent calls return false until that trial is resolved via
     * {@link #recordSuccess()} or {@link #recordFailure()}.
     */
    public synchronized boolean allowRequest() {
        switch (state) {
            case CLOSED -> {
                return true;
            }
            case HALF_OPEN -> {
                return false;
            }
            case OPEN -> {
                if (Duration.between(openedAt, clock.instant()).compareTo(cooldown) >= 0) {
                    state = State.HALF_OPEN;
                    halfOpenTrialInFlight = true;
                    return true;
                }
                return false;
            }
            default -> throw new IllegalStateException("Unknown state: " + state);
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
        halfOpenTrialInFlight = false;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State getState() {
        return state;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        halfOpenTrialInFlight = false;
    }
}
