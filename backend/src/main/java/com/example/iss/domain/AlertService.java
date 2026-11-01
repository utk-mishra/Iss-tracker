package com.example.iss.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure function producing the conditional alert strings for a single pass
 * (PROMPT.md sections 1, 5, 9). Only OBSERVABLE passes (see {@link Pass#isObservable()})
 * can ever produce alerts; a non-observable pass always yields an empty list.
 *
 * <p>Alert text is character-for-character as specified, evaluated and appended in
 * order 1, 2, 3, 4, with one {@code if} per rule.
 */
public final class AlertService {

    private static final String ALERT_1_TEMPLATE = "ISS visible soon - Check sky in %s";
    private static final String ALERT_2 = "Clear skies predicted - Excellent viewing opportunity";
    private static final String ALERT_3 = "Extended viewing opportunity";
    private static final String ALERT_4 = "Exceptionally bright pass - Easily visible";

    private static final Duration ALERT_1_WINDOW = Duration.ofHours(2);
    private static final double CLOUD_THRESHOLD_PCT = 20.0;
    private static final long DURATION_THRESHOLD_SEC = 300;
    private static final double MAGNITUDE_THRESHOLD = -2.0;

    /**
     * @param pass      the pass to evaluate; alerts never fire for a non-observable pass
     * @param cloudPct  cloud cover percentage at the pass time, or null if unknown
     * @param magnitude approximate brightness magnitude for this pass
     * @param now       current instant, used for the 2-hour "visible soon" window
     * @return alert strings in order 1,2,3,4; empty if none fire
     */
    public List<String> build(Pass pass, Double cloudPct, double magnitude, Instant now) {
        List<String> alerts = new ArrayList<>();
        if (!pass.isObservable()) {
            return alerts;
        }

        boolean alert1Condition = isWithinVisibleSoonWindow(pass.visibleStart(), now);

        if (alert1Condition) {
            alerts.add(String.format(ALERT_1_TEMPLATE, pass.riseDirection()));
        }
        if (alert1Condition && cloudPct != null && cloudPct < CLOUD_THRESHOLD_PCT) {
            alerts.add(ALERT_2);
        }
        if (pass.durationSec() > DURATION_THRESHOLD_SEC) {
            alerts.add(ALERT_3);
        }
        if (magnitude < MAGNITUDE_THRESHOLD) {
            alerts.add(ALERT_4);
        }
        return alerts;
    }

    private boolean isWithinVisibleSoonWindow(Instant visibleStart, Instant now) {
        if (visibleStart.isBefore(now)) {
            return false;
        }
        Instant windowEnd = now.plus(ALERT_1_WINDOW);
        return !visibleStart.isAfter(windowEnd);
    }
}
