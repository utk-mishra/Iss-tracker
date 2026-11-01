package com.example.iss.domain;

import java.time.Instant;

/**
 * Current ISS position, enriched with a constant altitude and a derived velocity
 * (PROMPT.md sections 3.1, 5, 6). Open-Notify provides no altitude/velocity; altitude
 * is a documented constant (408 km) and velocity is derived by {@link VelocityEstimator}
 * from consecutive samples.
 *
 * @param latitude     degrees, parsed from the upstream's string field
 * @param longitude    degrees, parsed from the upstream's string field
 * @param altitudeKm   constant 408 km approximation; Open-Notify gives no altitude
 * @param velocityKmh  derived ground velocity; see {@link VelocityEstimator}
 * @param observedAt   instant the position was reported (upstream's unix timestamp)
 * @param note         human-readable caveat about the constant-altitude approximation
 */
public record IssPosition(
        double latitude,
        double longitude,
        double altitudeKm,
        double velocityKmh,
        Instant observedAt,
        String note
) {
    public static final double CONSTANT_ALTITUDE_KM = 408.0;
    public static final String ALTITUDE_NOTE =
            "Altitude is a constant approximation (408 km); Open-Notify does not report altitude.";
}
