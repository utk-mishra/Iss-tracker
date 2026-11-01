package com.example.iss.service;

import com.example.iss.domain.Pass;

/**
 * A {@link Pass} enriched with request-time-computed fields that are not part of the
 * domain record itself (PROMPT.md section 6): cloud cover for EVERY pass, magnitude
 * ONLY for observable passes (Step 6 decision 4).
 *
 * @param pass           the underlying parsed pass
 * @param magnitude      approximate brightness magnitude; null if not observable
 * @param cloudCoverPct  cloud cover at the pass time; null if unknown/outside forecast
 */
public record EnrichedPass(Pass pass, Double magnitude, Integer cloudCoverPct) {
}
