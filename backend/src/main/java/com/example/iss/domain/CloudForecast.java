package com.example.iss.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Hourly cloud-cover forecast from Open-Meteo (PROMPT.md sections 3.3, 6).
 * {@code hourlyTimes} and {@code hourlyCloudCoverPct} are parallel arrays of equal
 * length, exactly 1 hour apart; a cloud-cover value may be null for a given hour.
 *
 * @param hourlyTimes          hourly instants, strictly increasing, 1-hour step
 * @param hourlyCloudCoverPct  cloud cover percentage per hour; null entries allowed
 */
public record CloudForecast(List<Instant> hourlyTimes, List<Integer> hourlyCloudCoverPct) {

    /**
     * Cloud cover nearest {@code target}, rounded to the nearest hour (round-half-up:
     * exactly 30 minutes past the hour rounds forward). Returns null if {@code target}
     * falls outside [first hour, last hour] of this forecast, or if the nearest hour's
     * own value is null.
     */
    public Integer cloudCoverPctAt(Instant target) {
        if (hourlyTimes.isEmpty()) {
            return null;
        }
        Instant first = hourlyTimes.get(0);
        Instant last = hourlyTimes.get(hourlyTimes.size() - 1);
        if (target.isBefore(first) || target.isAfter(last)) {
            return null;
        }

        int nearestIndex = 0;
        Duration smallestDiff = null;
        for (int i = 0; i < hourlyTimes.size(); i++) {
            Instant hour = hourlyTimes.get(i);
            Duration diff = Duration.between(hour, target).abs();
            if (smallestDiff == null || diff.compareTo(smallestDiff) < 0
                    || (diff.equals(smallestDiff) && hour.isAfter(hourlyTimes.get(nearestIndex)))) {
                smallestDiff = diff;
                nearestIndex = i;
            }
        }
        return hourlyCloudCoverPct.get(nearestIndex);
    }
}
