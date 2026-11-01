package com.example.iss.service;

import com.example.iss.resilience.Result;
import com.example.iss.resilience.Source;

/**
 * Per-upstream badge metadata exposed in API responses (PROMPT.md section 2: each
 * response has a per-upstream meta so the UI can badge each section separately).
 *
 * @param source   FRESH / STALE / MOCK
 * @param degraded true when source != FRESH
 * @param ageMs    age of the data relative to the request
 */
public record UpstreamMeta(Source source, boolean degraded, long ageMs) {

    public static UpstreamMeta from(Result<?> result) {
        return new UpstreamMeta(result.source(), result.source() != Source.FRESH, result.ageMs());
    }
}
