package com.example.iss.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded, TTL-aware cache with an injectable {@link Clock}.
 *
 * <p>An entry is considered <b>fresh</b> while its age is within {@code ttl}; it remains
 * readable (but reported as not fresh / STALE by the caller) until {@code maxStale}, at
 * which point it is evicted on next access. The cache is bounded to {@code maxEntries};
 * when full, the oldest entry (by insertion/re-insertion order) is evicted to make room.
 *
 * <p>Thread-safety: all mutating operations are synchronized; this cache is sized for
 * at most a few hundred entries (see PROMPT.md section 2: bounded to 500), so a simple
 * lock is sufficient and keeps the implementation easy to reason about.
 *
 * @param <V> the type of cached value
 */
public final class TtlCache<V> {

    /**
     * A cached value plus the instant it was stored, so callers can classify freshness.
     */
    public record Entry<V>(V value, Instant storedAt) {
    }

    private final Clock clock;
    private final Duration ttl;
    private final Duration maxStale;
    private final int maxEntries;
    private final LinkedHashMap<String, Entry<V>> store;

    public TtlCache(Clock clock, Duration ttl, Duration maxStale, int maxEntries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.maxStale = Objects.requireNonNull(maxStale, "maxStale");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        // access-order=false (insertion order); re-put moves key handling is done manually below.
        this.store = new LinkedHashMap<>();
    }

    /**
     * Stores (or replaces) the value for {@code key}, stamped with the current clock time.
     * Re-inserting an existing key refreshes its position so it is not treated as the
     * oldest entry for eviction purposes.
     */
    public synchronized void put(String key, V value) {
        Objects.requireNonNull(key, "key");
        store.remove(key);
        if (store.size() >= maxEntries) {
            evictOldest();
        }
        store.put(key, new Entry<>(value, clock.instant()));
    }

    /**
     * Returns the entry for {@code key} if present and not older than {@code maxStale}.
     * Entries older than {@code maxStale} are evicted and treated as absent.
     */
    public synchronized Optional<Entry<V>> get(String key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (age(entry).compareTo(maxStale) > 0) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * True if the given entry's age is within the TTL (FRESH); false means it is STALE
     * (readable but expired) relative to the clock at the time of this call.
     */
    public boolean isFresh(Entry<V> entry) {
        return age(entry).compareTo(ttl) <= 0;
    }

    public synchronized int size() {
        return store.size();
    }

    private Duration age(Entry<V> entry) {
        return Duration.between(entry.storedAt(), clock.instant());
    }

    private void evictOldest() {
        var iterator = store.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
