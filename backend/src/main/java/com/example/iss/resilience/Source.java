package com.example.iss.resilience;

/**
 * Degradation level of a resolved {@link Result}. See PROMPT.md section 2
 * (degradation table): FRESH = live or unexpired cache; STALE = expired cache entry
 * served because upstream was unavailable; MOCK = static fallback because nothing
 * usable was cached. "Forced offline" is not a separate level: it only restricts how
 * FRESH/STALE/MOCK are chosen (see ResilientFetcher), never touching the network.
 */
public enum Source {
    FRESH,
    STALE,
    MOCK
}
