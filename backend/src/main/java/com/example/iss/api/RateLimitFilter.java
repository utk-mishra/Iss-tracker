package com.example.iss.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-written per-IP token bucket rate limiter (PROMPT.md section 8; Step 6 decisions
 * 2/7 and addition a). Applies to /api/** only. Keyed by {@code request.getRemoteAddr()}
 * by default; X-Forwarded-For is honoured ONLY when {@code trustedProxy=true}, in which
 * case the right-most entry is used (the one closest to our server, i.e. the one a
 * trusted immediate proxy actually set - earlier entries can be spoofed by the client).
 * The bucket map is bounded (~10,000 entries) with idle-bucket eviction on every write
 * to avoid unbounded memory growth. Always sends {@code Retry-After} on 429.
 */
public final class RateLimitFilter extends HttpFilter {

    private static final int MAX_BUCKETS = 10_000;
    private static final Duration IDLE_EVICTION_AGE = Duration.ofMinutes(5);
    private static final String API_PATH_PREFIX = "/api/";

    private static final class Bucket {
        double tokens;
        Instant lastRefill;
        volatile Instant lastAccess;
    }

    private final Clock clock;
    private final int limitPerMinute;
    private final boolean trustedProxy;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(Clock clock, int limitPerMinute, boolean trustedProxy) {
        this.clock = clock;
        this.limitPerMinute = limitPerMinute;
        this.trustedProxy = trustedProxy;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!request.getRequestURI().startsWith(API_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveClientKey(request);
        if (tryConsume(key)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\",\"status\":429}");
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        if (trustedProxy) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] parts = forwardedFor.split(",");
                return parts[parts.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private synchronized boolean tryConsume(String key) {
        evictIdleBucketsIfNeeded();
        Instant now = clock.instant();
        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            Bucket b = new Bucket();
            b.tokens = limitPerMinute;
            b.lastRefill = now;
            b.lastAccess = now;
            return b;
        });

        refill(bucket, now);
        bucket.lastAccess = now;

        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill(Bucket bucket, Instant now) {
        Duration elapsed = Duration.between(bucket.lastRefill, now);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return;
        }
        double tokensToAdd = (elapsed.toMillis() / 60_000.0) * limitPerMinute;
        if (tokensToAdd > 0) {
            bucket.tokens = Math.min(limitPerMinute, bucket.tokens + tokensToAdd);
            bucket.lastRefill = now;
        }
    }

    private void evictIdleBucketsIfNeeded() {
        if (buckets.size() < MAX_BUCKETS) {
            return;
        }
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().lastAccess, now).compareTo(IDLE_EVICTION_AGE) > 0);
    }
}
