package com.example.iss.resilience;

/**
 * Outcome of a {@link ResilientFetcher#fetch} call.
 *
 * @param data   the resolved value (live, cached, or mock)
 * @param source which degradation level produced {@code data}
 * @param ageMs  how old {@code data} is, in milliseconds, relative to the fetch time;
 *               0 for freshly-fetched-from-upstream or MOCK data (a mock has no
 *               meaningful age).
 * @param <T> the type of the fetched value
 */
public record Result<T>(T data, Source source, long ageMs) {
}
