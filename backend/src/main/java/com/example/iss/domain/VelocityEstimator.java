package com.example.iss.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives ISS ground velocity (km/h) from consecutive position samples
 * (PROMPT.md section 5): haversine arc length between the previous and current
 * sample, scaled by (R+408)/R to account for orbital altitude, divided by the
 * elapsed time. Falls back to the documented default of 27,600 km/h when there is
 * no previous sample or the timestamp did not advance (including going backwards).
 *
 * <p>Thread-safe via a single {@link AtomicReference} holding the previous sample,
 * swapped atomically with {@link AtomicReference#getAndSet}; this must be called
 * INSIDE the upstream callable (not on a cache hit) so cache hits never corrupt
 * estimator state (see ResilientFetcher / IssFacade in later steps).
 */
public final class VelocityEstimator {

    private static final double DEFAULT_VELOCITY_KMH = 27_600.0;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ISS_ALTITUDE_KM = 408.0;
    private static final double ALTITUDE_SCALE = (EARTH_RADIUS_KM + ISS_ALTITUDE_KM) / EARTH_RADIUS_KM;

    private record Sample(double latitude, double longitude, Instant timestamp) {
    }

    private final AtomicReference<Sample> previous = new AtomicReference<>();

    public double estimateKmh(double latitude, double longitude, Instant timestamp) {
        Sample current = new Sample(latitude, longitude, timestamp);
        Sample prior = previous.getAndSet(current);

        if (prior == null) {
            return DEFAULT_VELOCITY_KMH;
        }
        Duration elapsed = Duration.between(prior.timestamp(), timestamp);
        if (elapsed.isZero() || elapsed.isNegative()) {
            return DEFAULT_VELOCITY_KMH;
        }

        double groundDistanceKm = haversineKm(prior.latitude(), prior.longitude(), latitude, longitude);
        double scaledDistanceKm = groundDistanceKm * ALTITUDE_SCALE;
        double elapsedHours = elapsed.toMillis() / 3_600_000.0;
        return scaledDistanceKm / elapsedHours;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
