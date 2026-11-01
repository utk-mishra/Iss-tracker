package com.example.iss.domain;

/**
 * Approximates apparent brightness magnitude from a pass's maximum elevation
 * (PROMPT.md section 5). This is a documented approximation, not photometry: real
 * ISS brightness also depends on solar phase angle, range, and panel orientation.
 * Only meaningful for observable passes (see {@link Pass#isObservable()}).
 */
public final class MagnitudeCalculator {

    private MagnitudeCalculator() {
    }

    public static double fromMaxElevationDeg(double maxElevationDeg) {
        if (maxElevationDeg >= 80) {
            return -3.5;
        }
        if (maxElevationDeg >= 40) {
            return -2.5;
        }
        if (maxElevationDeg >= 20) {
            return -1.5;
        }
        return -0.5;
    }
}
