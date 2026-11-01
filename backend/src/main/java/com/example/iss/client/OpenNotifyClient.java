package com.example.iss.client;

import com.example.iss.domain.IssPosition;
import com.example.iss.domain.VelocityEstimator;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Adapter for Open-Notify ISS position (PROMPT.md section 3.1). {@link #parse(JsonNode)}
 * is static and pure: it validates and extracts lat/lon/observedAt only. Velocity is
 * NOT computed here - {@link #fetch()} (the actual upstream callable passed to
 * ResilientFetcher) calls parse() then the injected {@link VelocityEstimator}, so
 * cache hits (which skip fetch() entirely) never corrupt estimator state.
 */
public final class OpenNotifyClient implements PositionClient {

    /** Validated, parsed fields from the Open-Notify response, before velocity is derived. */
    public record ParsedPosition(double latitude, double longitude, Instant observedAt) {
    }

    private final HttpJson httpJson;
    private final VelocityEstimator velocityEstimator;
    private final URI uri;

    public OpenNotifyClient(HttpJson httpJson, VelocityEstimator velocityEstimator, String url) {
        this.httpJson = Objects.requireNonNull(httpJson, "httpJson");
        this.velocityEstimator = Objects.requireNonNull(velocityEstimator, "velocityEstimator");
        this.uri = URI.create(url);
    }

    @Override
    public IssPosition fetch() {
        JsonNode json = httpJson.getJson(uri);
        ParsedPosition parsed = parse(json);
        double velocityKmh = velocityEstimator.estimateKmh(parsed.latitude(), parsed.longitude(), parsed.observedAt());
        return new IssPosition(
                parsed.latitude(),
                parsed.longitude(),
                IssPosition.CONSTANT_ALTITUDE_KM,
                velocityKmh,
                parsed.observedAt(),
                IssPosition.ALTITUDE_NOTE
        );
    }

    /**
     * Pure parse: reject unless message == "success" and lat/lon are in range.
     * latitude/longitude arrive as STRINGS; parsed via Double.parseDouble (locale-independent).
     */
    public static ParsedPosition parse(JsonNode json) {
        String message = json.path("message").asText("");
        if (!"success".equals(message)) {
            throw new IllegalArgumentException("Open-Notify message was not 'success': " + message);
        }

        JsonNode positionNode = json.path("iss_position");
        double latitude = Double.parseDouble(positionNode.path("latitude").asText());
        double longitude = Double.parseDouble(positionNode.path("longitude").asText());
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Open-Notify latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Open-Notify longitude out of range: " + longitude);
        }

        long timestampSeconds = json.path("timestamp").asLong();
        Instant observedAt = Instant.ofEpochSecond(timestampSeconds);

        return new ParsedPosition(latitude, longitude, observedAt);
    }
}
