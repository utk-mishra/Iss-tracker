package com.example.iss.client;

import com.example.iss.domain.Compass;
import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter for Pollux Labs pass predictions (PROMPT.md section 3.2).
 * {@link #parse(JsonNode)} is static and pure. A malformed pass (missing a required
 * field) is skipped, not fatal. A visible pass with missing window fields is kept as
 * NON-observable rather than dropped. Rise/set directions are the API's own 16-point
 * compass strings, stored verbatim; {@link Compass#fromAzimuthDeg(double)} is used only
 * as a fallback when the string is missing or blank.
 */
public final class PolluxClient implements PassesClient {

    private final HttpJson httpJson;
    private final String baseUrl;
    private final int passCount;

    public PolluxClient(HttpJson httpJson, String baseUrl, int passCount) {
        this.httpJson = Objects.requireNonNull(httpJson, "httpJson");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.passCount = passCount;
    }

    @Override
    public PassFeed fetch(double lat, double lon) {
        URI uri = URI.create(buildUrl(lat, lon));
        JsonNode json = httpJson.getJson(uri);
        return parse(json);
    }

    /**
     * Builds the Pollux Labs URL with locale-independent decimal formatting
     * (always ".", never a locale-dependent separator) and {@code n} always sent.
     */
    public String buildUrl(double lat, double lon) {
        return String.format(Locale.ROOT, "%s?lat=%.4f&lon=%.4f&n=%d", baseUrl, lat, lon, passCount);
    }

    public static PassFeed parse(JsonNode json) {
        double tleAgeHours = json.path("tle_age_hours").asDouble();
        boolean tleStale = json.path("tle_stale").asBoolean(false);

        List<Pass> passes = new ArrayList<>();
        for (JsonNode passNode : json.path("passes")) {
            Pass pass = tryParsePass(passNode);
            if (pass != null) {
                passes.add(pass);
            }
        }
        return new PassFeed(passes, tleAgeHours, tleStale);
    }

    private static Pass tryParsePass(JsonNode passNode) {
        try {
            return parsePass(passNode);
        } catch (RuntimeException e) {
            // One malformed pass must be skipped, not fail the whole parse (section 3.2).
            return null;
        }
    }

    private static Pass parsePass(JsonNode passNode) {
        JsonNode riseNode = requireField(passNode, "rise");
        JsonNode culminationNode = requireField(passNode, "culmination");
        JsonNode setNode = requireField(passNode, "set");

        Instant riseTime = Instant.parse(requireText(riseNode, "time"));
        double riseAzimuthDeg = requireDouble(riseNode, "azimuth_deg");
        String riseDirection = resolveDirection(riseNode, riseAzimuthDeg);

        Instant culminationTime = Instant.parse(requireText(culminationNode, "time"));
        double maxElevationDeg = requireDouble(culminationNode, "elevation_deg");

        Instant setTime = Instant.parse(requireText(setNode, "time"));
        double setAzimuthDeg = requireDouble(setNode, "azimuth_deg");
        String setDirection = resolveDirection(setNode, setAzimuthDeg);

        long durationSec = requireLong(passNode, "duration_sec");
        boolean visible = passNode.path("visible").asBoolean(false);

        Instant visibleStart = null;
        Instant visibleEnd = null;
        long visibleDurationSec = 0;
        if (visible && passNode.hasNonNull("visible_start") && passNode.hasNonNull("visible_end")) {
            visibleStart = Instant.parse(passNode.get("visible_start").asText());
            visibleEnd = Instant.parse(passNode.get("visible_end").asText());
            visibleDurationSec = passNode.path("visible_duration_sec").asLong(0);
        }
        // A visible pass with missing window fields is kept as NON-observable, not dropped
        // (visible stays true, but visibleStart/End/DurationSec default to null/0 above,
        // which already makes Pass.isObservable() return false).

        return new Pass(
                riseTime, riseAzimuthDeg, riseDirection,
                culminationTime, maxElevationDeg,
                setTime, setAzimuthDeg, setDirection,
                durationSec,
                visible, visibleStart, visibleEnd, visibleDurationSec
        );
    }

    private static String resolveDirection(JsonNode node, double azimuthDeg) {
        String compass = node.path("compass").asText("");
        if (compass == null || compass.isBlank()) {
            return Compass.fromAzimuthDeg(azimuthDeg).name();
        }
        return compass;
    }

    private static JsonNode requireField(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return node;
    }

    private static String requireText(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return node.get(field).asText();
    }

    private static double requireDouble(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return node.get(field).asDouble();
    }

    private static long requireLong(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return node.get(field).asLong();
    }
}
