package com.example.iss.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TtlCache<V>: bounded, injectable-Clock cache with TTL (fresh window) and max-stale
 * (hard eviction) semantics. See PROMPT.md section 2 (TTL/max-stale table) and section 9.
 */
class TtlCacheTest {

    private static final Duration TTL = Duration.ofSeconds(5);
    private static final Duration MAX_STALE = Duration.ofSeconds(60);

    private MutableClock clock;
    private TtlCache<String> cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        cache = new TtlCache<>(clock, TTL, MAX_STALE, 3);
    }

    @Nested
    @DisplayName("fresh entries")
    class Fresh {

        @Test
        @DisplayName("given an entry just stored, when read immediately, then it is present and fresh")
        void givenEntryJustStored_whenReadImmediately_thenPresentAndFresh() {
            cache.put("key", "value");

            Optional<TtlCache.Entry<String>> result = cache.get("key");

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("value");
            assertThat(cache.isFresh(result.get())).isTrue();
        }

        @Test
        @DisplayName("given an entry stored, when read at exactly the TTL boundary, then it is still fresh")
        void givenEntryStored_whenReadAtExactTtlBoundary_thenStillFresh() {
            cache.put("key", "value");
            clock.advance(TTL);

            Optional<TtlCache.Entry<String>> result = cache.get("key");

            assertThat(result).isPresent();
            assertThat(cache.isFresh(result.get())).isTrue();
        }
    }

    @Nested
    @DisplayName("stale entries (expired TTL but within max-stale)")
    class Stale {

        @Test
        @DisplayName("given an entry older than TTL but younger than max-stale, when read, then present but not fresh")
        void givenEntryOlderThanTtl_whenRead_thenPresentButNotFresh() {
            cache.put("key", "value");
            clock.advance(TTL.plusSeconds(1));

            Optional<TtlCache.Entry<String>> result = cache.get("key");

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("value");
            assertThat(cache.isFresh(result.get())).isFalse();
        }
    }

    @Nested
    @DisplayName("entries expired beyond max-stale")
    class ExpiredBeyondMaxStale {

        @Test
        @DisplayName("given an entry older than max-stale, when read, then it is absent (evicted)")
        void givenEntryOlderThanMaxStale_whenRead_thenAbsent() {
            cache.put("key", "value");
            clock.advance(MAX_STALE.plusSeconds(1));

            Optional<TtlCache.Entry<String>> result = cache.get("key");

            assertThat(result).isEmpty();
            assertThat(cache.size()).isZero();
        }

        @Test
        @DisplayName("given an entry exactly at max-stale boundary, when read, then it is still present")
        void givenEntryExactlyAtMaxStaleBoundary_whenRead_thenStillPresent() {
            cache.put("key", "value");
            clock.advance(MAX_STALE);

            Optional<TtlCache.Entry<String>> result = cache.get("key");

            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("bounded eviction")
    class Eviction {

        @Test
        @DisplayName("given the cache is at max entries, when a new key is put, then the oldest entry is evicted")
        void givenCacheAtMaxEntries_whenNewKeyPut_thenOldestEvicted() {
            cache.put("a", "1");
            clock.advance(Duration.ofSeconds(1));
            cache.put("b", "2");
            clock.advance(Duration.ofSeconds(1));
            cache.put("c", "3");

            clock.advance(Duration.ofSeconds(1));
            cache.put("d", "4");

            assertThat(cache.size()).isEqualTo(3);
            assertThat(cache.get("a")).isEmpty();
            assertThat(cache.get("b")).isPresent();
            assertThat(cache.get("c")).isPresent();
            assertThat(cache.get("d")).isPresent();
        }

        @Test
        @DisplayName("given a key is re-put, when evicting, then it counts as freshly inserted, not oldest")
        void givenKeyRePut_whenEvicting_thenCountsAsFreshlyInserted() {
            cache.put("a", "1");
            clock.advance(Duration.ofSeconds(1));
            cache.put("b", "2");
            clock.advance(Duration.ofSeconds(1));
            cache.put("a", "1-updated");
            clock.advance(Duration.ofSeconds(1));
            cache.put("c", "3");

            clock.advance(Duration.ofSeconds(1));
            cache.put("d", "4");

            assertThat(cache.get("b")).isEmpty();
            assertThat(cache.get("a").orElseThrow().value()).isEqualTo("1-updated");
            assertThat(cache.get("c")).isPresent();
            assertThat(cache.get("d")).isPresent();
        }
    }
}
