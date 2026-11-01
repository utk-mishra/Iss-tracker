package com.example.iss.domain;

import java.util.List;

/**
 * A batch of pass predictions plus orbital-data freshness metadata from Pollux Labs
 * (PROMPT.md sections 3.2, 6).
 *
 * @param passes        the parsed passes, in upstream order; malformed entries skipped
 * @param tleAgeHours   age of the orbital element set used to compute these passes
 * @param tleStale      whether the upstream considers its own TLE stale
 */
public record PassFeed(List<Pass> passes, double tleAgeHours, boolean tleStale) {
}
