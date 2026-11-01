package com.example.iss.domain;

import java.time.Instant;

/**
 * A single ISS overhead pass prediction, already normalised from the Pollux Labs
 * payload shape into domain terms (PROMPT.md sections 3.2, 5, 6).
 *
 * <p>{@code visible} and {@code visibleDurationSec} come straight from the upstream
 * ({@code visible_duration_sec} is 0 when absent). A pass is OBSERVABLE when
 * {@code visible == true AND visibleDurationSec > 0} (see {@link #isObservable()}).
 *
 * <p>{@code riseDirection}/{@code setDirection} are the upstream's own 16-point compass
 * strings (e.g. "NNW", "ESE"), stored verbatim as Strings, never parsed via
 * {@code Compass.valueOf}. {@link PolluxClient} falls back to
 * {@link Compass#fromAzimuthDeg(double)}.name() only when the upstream string is
 * missing or blank (PROMPT.md section 3.2).
 *
 * @param riseTime            rise instant
 * @param riseAzimuthDeg      rise compass azimuth in degrees
 * @param riseDirection       rise compass direction string, as received from the API
 *                            (or a Compass-derived fallback if missing/blank)
 * @param culminationTime     instant of maximum elevation
 * @param maxElevationDeg     maximum elevation in degrees during the pass
 * @param setTime             set instant
 * @param setAzimuthDeg       set compass azimuth in degrees
 * @param setDirection        set compass direction string, as received from the API
 *                            (or a Compass-derived fallback if missing/blank)
 * @param durationSec         rise-to-set duration in seconds
 * @param visible             whether the pass is above horizon AND dark-sky AND sunlit
 * @param visibleStart        start of the visible window; null if not visible
 * @param visibleEnd          end of the visible window; null if not visible
 * @param visibleDurationSec  length of the visible window in seconds; 0 if not visible
 *                            or if the upstream reported a zero-length window
 */
public record Pass(
        Instant riseTime,
        double riseAzimuthDeg,
        String riseDirection,
        Instant culminationTime,
        double maxElevationDeg,
        Instant setTime,
        double setAzimuthDeg,
        String setDirection,
        long durationSec,
        boolean visible,
        Instant visibleStart,
        Instant visibleEnd,
        long visibleDurationSec
) {

    /**
     * OBSERVABLE PASS = visible == true AND visibleDurationSec > 0 (PROMPT.md section 5).
     * A visible pass with a zero-length window (seen live in pollux-passes-n20.json) is
     * NOT observable: it can never be the next visible pass and never triggers alerts.
     */
    public boolean isObservable() {
        return visible && visibleDurationSec > 0;
    }
}
