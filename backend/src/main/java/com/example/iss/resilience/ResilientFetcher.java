package com.example.iss.resilience;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The single fallback chain every upstream call goes through (PROMPT.md section 2).
 *
 * <pre>
 * fetch(key, upstreamCall, mockSupplier, offline):
 *   if offline:
 *       cached = cache.get(key)
 *       return cached.present ? STALE(cached) : MOCK(mockSupplier.get())
 *
 *   cached = cache.get(key)
 *   if cached.present AND cache.isFresh(cached):
 *       return FRESH(cached)                      # cache-aside hit, no network touched
 *
 *   if breaker.allowRequest():
 *       try:
 *           data = singleFlight.execute(key, () -> callWithOneRetry(upstreamCall))
 *           cache.put(key, data)                   # MOCK results are NEVER cached
 *           breaker.recordSuccess()
 *           return FRESH(data)
 *       catch (Exception e):
 *           breaker.recordFailure()
 *           # fall through to stale/mock
 *
 *   cached = cache.get(key)                        # re-check; may exist even if not "fresh"
 *   return cached.present ? STALE(cached) : MOCK(mockSupplier.get())
 * </pre>
 *
 * <p>Timeout (3s) is NOT enforced here: it is the HTTP client's responsibility (Step 5)
 * to configure a request timeout on {@code upstreamCall}, so a slow upstream simply makes
 * the callable throw (e.g. {@code HttpTimeoutException}), which this chain already
 * handles as a generic failure. This keeps {@code ResilientFetcher} HTTP-agnostic.
 *
 * <p>Retry is immediate (no backoff, no sleep) and happens inside a single {@code fetch()}
 * call: the circuit breaker observes at most one success/failure per {@code fetch()},
 * never one per retry attempt.
 *
 * @param <T> the type of value being fetched
 */
public final class ResilientFetcher<T> {

    private final TtlCache<T> cache;
    private final CircuitBreaker breaker;
    private final SingleFlight<T> singleFlight;
    private final Clock clock;
    private final int retryCount;

    public ResilientFetcher(TtlCache<T> cache, CircuitBreaker breaker, SingleFlight<T> singleFlight,
                             Clock clock, int retryCount) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.breaker = Objects.requireNonNull(breaker, "breaker");
        this.singleFlight = Objects.requireNonNull(singleFlight, "singleFlight");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        this.retryCount = retryCount;
    }

    /**
     * Resolves a value for {@code key} through the fallback chain described above.
     *
     * @param key          cache/single-flight key (e.g. rounded lat/lon)
     * @param upstreamCall performs exactly one upstream HTTP call + parse when invoked
     * @param mockSupplier produces the static MOCK fallback value; never cached
     * @param offline      when true, the network is never touched: STALE if cached, else MOCK
     */
    public Result<T> fetch(String key, Callable<T> upstreamCall, Supplier<T> mockSupplier, boolean offline) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(upstreamCall, "upstreamCall");
        Objects.requireNonNull(mockSupplier, "mockSupplier");

        if (offline) {
            return staleOrMock(key, mockSupplier);
        }

        Optional<TtlCache.Entry<T>> cached = cache.get(key);
        if (cached.isPresent() && cache.isFresh(cached.get())) {
            return toResult(cached.get(), Source.FRESH);
        }

        if (breaker.allowRequest()) {
            try {
                T data = singleFlight.execute(key, () -> callWithRetry(upstreamCall));
                cache.put(key, data);
                breaker.recordSuccess();
                return new Result<>(data, Source.FRESH, 0L);
            } catch (Exception e) {
                breaker.recordFailure();
                // fall through to stale/mock below
            }
        }

        return staleOrMock(key, mockSupplier);
    }

    private T callWithRetry(Callable<T> upstreamCall) throws Exception {
        Exception lastFailure;
        try {
            return upstreamCall.call();
        } catch (Exception first) {
            lastFailure = first;
        }
        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                return upstreamCall.call();
            } catch (Exception retryFailure) {
                lastFailure = retryFailure;
            }
        }
        throw lastFailure;
    }

    private Result<T> staleOrMock(String key, Supplier<T> mockSupplier) {
        Optional<TtlCache.Entry<T>> cached = cache.get(key);
        if (cached.isPresent()) {
            return toResult(cached.get(), Source.STALE);
        }
        return new Result<>(mockSupplier.get(), Source.MOCK, 0L);
    }

    private Result<T> toResult(TtlCache.Entry<T> entry, Source source) {
        long ageMs = Duration.between(entry.storedAt(), clock.instant()).toMillis();
        return new Result<>(entry.value(), source, ageMs);
    }
}
