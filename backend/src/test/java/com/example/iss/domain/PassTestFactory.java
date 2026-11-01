package com.example.iss.domain;

import java.time.Instant;

/**
 * Test-only factory for building {@link Pass} instances without long positional
 * constructor calls scattered across tests (Step 5 decision b). Centralising
 * construction here means the single place that needs updating if Pass's shape
 * changes again is this factory, not every test.
 */
final class PassTestFactory {

    private PassTestFactory() {
    }

    /**
     * An observable pass (visible=true, visibleDurationSec > 0) with the given visible
     * window, rise-to-set duration, and rise azimuth/direction. Rise direction defaults
     * to "N" unless overridden via {@link #observablePassWithRiseDirection}.
     */
    static Pass observablePass(Instant visibleStart, Instant visibleEnd, long durationSec, double riseAzimuthDeg) {
        return observablePassWithRiseDirection(visibleStart, visibleEnd, durationSec, riseAzimuthDeg, "N");
    }

    static Pass observablePassWithRiseDirection(Instant visibleStart, Instant visibleEnd, long durationSec,
                                                 double riseAzimuthDeg, String riseDirection) {
        return new Pass(
                visibleStart.minusSeconds(60), riseAzimuthDeg, riseDirection,
                visibleStart, 50.0,
                visibleEnd.plusSeconds(60), 90.0, "E",
                durationSec,
                true, visibleStart, visibleEnd,
                visibleEnd.getEpochSecond() - visibleStart.getEpochSecond()
        );
    }

    static Pass nonVisiblePass(Instant riseTime, double riseAzimuthDeg, Instant culminationTime,
                                double maxElevationDeg, Instant setTime, double setAzimuthDeg, long durationSec) {
        return new Pass(
                riseTime, riseAzimuthDeg, "N",
                culminationTime, maxElevationDeg,
                setTime, setAzimuthDeg, "E",
                durationSec,
                false, null, null, 0
        );
    }

    static Pass visiblePassWithZeroDuration(Instant visibleStart, double riseAzimuthDeg, long durationSec) {
        return new Pass(
                visibleStart.minusSeconds(60), riseAzimuthDeg, "N",
                visibleStart, 50.0,
                visibleStart.plusSeconds(60), 90.0, "E",
                durationSec,
                true, visibleStart, visibleStart, 0
        );
    }
}
