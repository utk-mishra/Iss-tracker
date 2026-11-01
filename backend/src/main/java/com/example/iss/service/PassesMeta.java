package com.example.iss.service;

/**
 * Pass-feed metadata: the generic {@link UpstreamMeta} plus Pollux-specific
 * orbital-data freshness fields (PROMPT.md section 6: meta.passes also carries
 * tleAgeHours, tleStale).
 */
public record PassesMeta(UpstreamMeta meta, double tleAgeHours, boolean tleStale) {
}
