package com.example.iss.domain;

/**
 * 16-point compass direction, derived from an azimuth in degrees (PROMPT.md section 5).
 * Azimuth is normalised into [0, 360) before being bucketed into one of 16 fallback
 * directions via {@code dirs[round(az/22.5) % 16]}. A NaN azimuth is treated safely and
 * never throws; it resolves to {@link #N} as a defensive default.
 *
 * <p>This fallback is used ONLY when the upstream Pollux Labs compass string is missing
 * or blank; when present, the API's own 16-point string (e.g. "NNW", "ESE") is stored
 * verbatim as a plain String on {@link Pass}, not parsed via {@link #valueOf(String)}
 * (PROMPT.md section 3.2 decision).
 */
public enum Compass {
    N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW;

    private static final Compass[] DIRECTIONS = values();
    private static final double SECTOR_WIDTH_DEG = 360.0 / DIRECTIONS.length;

    public static Compass fromAzimuthDeg(double azimuthDeg) {
        if (Double.isNaN(azimuthDeg)) {
            return N;
        }
        double normalised = ((azimuthDeg % 360.0) + 360.0) % 360.0;
        int index = (int) Math.round(normalised / SECTOR_WIDTH_DEG) % DIRECTIONS.length;
        return DIRECTIONS[index];
    }
}
