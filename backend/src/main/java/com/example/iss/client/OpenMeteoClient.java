package com.example.iss.client;

import com.example.iss.domain.CloudForecast;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter for Open-Meteo hourly cloud cover (PROMPT.md section 3.3).
 * {@link #parse(JsonNode)} is static and pure: times have no "Z" suffix, so they are
 * parsed via LocalDateTime + ZoneOffset.UTC (valid because the request always asks for
 * timezone=UTC). The 1-hour step and equal array lengths are verified; any violation is
 * a generic parse failure (treated like a timeout by ResilientFetcher).
 */
public final class OpenMeteoClient implements CloudClient {

    private static final long ONE_HOUR_SECONDS = 3600;

    private final HttpJson httpJson;
    private final String baseUrl;

    public OpenMeteoClient(HttpJson httpJson, String baseUrl) {
        this.httpJson = Objects.requireNonNull(httpJson, "httpJson");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }

    @Override
    public CloudForecast fetch(double lat, double lon) {
        URI uri = URI.create(buildUrl(lat, lon));
        JsonNode json = httpJson.getJson(uri);
        return parse(json);
    }

    /**
     * Builds the Open-Meteo URL with locale-independent decimal formatting and the
     * fixed query parameters required by PROMPT.md section 3.3.
     */
    public String buildUrl(double lat, double lon) {
        return String.format(Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&hourly=cloud_cover&timezone=UTC&forecast_days=10",
                baseUrl, lat, lon);
    }

    public static CloudForecast parse(JsonNode json) {
        JsonNode hourly = json.path("hourly");
        JsonNode timeArray = hourly.path("time");
        JsonNode cloudCoverArray = hourly.path("cloud_cover");

        if (timeArray.size() != cloudCoverArray.size()) {
            throw new IllegalArgumentException(
                    "time and cloud_cover arrays have different lengths: "
                            + timeArray.size() + " vs " + cloudCoverArray.size());
        }

        List<Instant> hourlyTimes = new ArrayList<>();
        List<Integer> hourlyCloudCoverPct = new ArrayList<>();

        Instant previous = null;
        for (int i = 0; i < timeArray.size(); i++) {
            String noZTime = timeArray.get(i).asText();
            Instant current = LocalDateTime.parse(noZTime).toInstant(ZoneOffset.UTC);

            if (previous != null) {
                long diffSeconds = current.getEpochSecond() - previous.getEpochSecond();
                if (diffSeconds != ONE_HOUR_SECONDS) {
                    throw new IllegalArgumentException(
                            "Expected exactly 1-hour step between entries, got " + diffSeconds + "s at index " + i);
                }
            }
            previous = current;

            hourlyTimes.add(current);
            JsonNode cloudNode = cloudCoverArray.get(i);
            hourlyCloudCoverPct.add(cloudNode == null || cloudNode.isNull() ? null : cloudNode.asInt());
        }

        return new CloudForecast(hourlyTimes, hourlyCloudCoverPct);
    }
}
