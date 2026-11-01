package com.example.iss.client;

import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the static MOCK fallbacks for position and cloud (PROMPT.md section 9,
 * Step 5 decision f). mocks/cloud.json itself is never edited: its 24-hour
 * hour-of-day curve is parsed once via {@link OpenMeteoClient#parse(JsonNode)} and
 * then repeated in memory to cover a full 240-hour (10-day) window, re-anchored so
 * the first entry is always today's 00:00 UTC per the injected Clock. This guarantees
 * a shifted mock pass can never fall outside the forecast window, even when "now" is
 * late enough (e.g. 23:30 UTC) that the pass's +90-minute shift crosses midnight.
 */
public final class MockData {

    private static final int HOURS_PER_DAY = 24;
    private static final int TOTAL_HOURS = 240;

    private MockData() {
    }

    /**
     * Default velocity for the MOCK position: there is no previous sample to derive a
     * real velocity from, so this mirrors VelocityEstimator's own no-prior-sample
     * default (PROMPT.md section 5) rather than inventing a separate mock-specific value.
     */
    private static final double DEFAULT_VELOCITY_KMH = 27_600.0;

    /** Position mock is deliberately NOT time-shifted (no "next event" semantics). */
    public static IssPosition loadPosition(JsonNode mockPositionJson) {
        OpenNotifyClient.ParsedPosition parsed = OpenNotifyClient.parse(mockPositionJson);
        return new IssPosition(
                parsed.latitude(),
                parsed.longitude(),
                IssPosition.CONSTANT_ALTITUDE_KM,
                DEFAULT_VELOCITY_KMH,
                parsed.observedAt(),
                IssPosition.ALTITUDE_NOTE
        );
    }

    public static CloudForecast loadCloud(JsonNode mockCloudJson, Clock clock) {
        CloudForecast original = OpenMeteoClient.parse(mockCloudJson);
        List<Integer> hourOfDayCurve = extractHourOfDayCurve(original);

        Instant todayMidnightUtc = clock.instant().atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        List<Instant> repeatedTimes = new ArrayList<>(TOTAL_HOURS);
        List<Integer> repeatedCloud = new ArrayList<>(TOTAL_HOURS);
        for (int hour = 0; hour < TOTAL_HOURS; hour++) {
            repeatedTimes.add(todayMidnightUtc.plus(Duration.ofHours(hour)));
            repeatedCloud.add(hourOfDayCurve.get(hour % HOURS_PER_DAY));
        }
        return new CloudForecast(repeatedTimes, repeatedCloud);
    }

    /**
     * Extracts the mock's 24-hour cloud_cover curve, indexed by hour-of-day (0-23),
     * from whatever absolute day the mock file happens to cover.
     */
    private static List<Integer> extractHourOfDayCurve(CloudForecast original) {
        List<Integer> curve = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY && hour < original.hourlyCloudCoverPct().size(); hour++) {
            curve.add(original.hourlyCloudCoverPct().get(hour));
        }
        if (curve.size() < HOURS_PER_DAY) {
            throw new IllegalStateException("mocks/cloud.json must cover at least 24 hourly entries");
        }
        return curve;
    }
}
