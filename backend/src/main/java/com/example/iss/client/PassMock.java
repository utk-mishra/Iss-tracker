package com.example.iss.client;

import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the static mocks/passes.json fallback and time-shifts every instant so the
 * first OBSERVABLE pass starts ~90 minutes from "now" per the injected Clock
 * (PROMPT.md section 9, Step 5 decision f). Reuses {@link PolluxClient#parse(JsonNode)}
 * since the mock is schema-identical to the real Pollux response by design. Labeling
 * the result as MOCK in a response's meta is the caller's (IssFacade, Step 6)
 * responsibility, not this loader's.
 */
public final class PassMock {

    private static final Duration ANCHOR_OFFSET = Duration.ofMinutes(90);

    private PassMock() {
    }

    public static PassFeed load(JsonNode mockPassesJson, Clock clock) {
        PassFeed original = PolluxClient.parse(mockPassesJson);

        Pass firstObservable = original.passes().stream()
                .filter(Pass::isObservable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "mocks/passes.json must contain at least one observable pass"));

        Instant targetStart = clock.instant().plus(ANCHOR_OFFSET);
        Duration shiftDelta = Duration.between(firstObservable.visibleStart(), targetStart);

        List<Pass> shiftedPasses = new ArrayList<>();
        for (Pass pass : original.passes()) {
            shiftedPasses.add(shift(pass, shiftDelta));
        }
        return new PassFeed(shiftedPasses, original.tleAgeHours(), original.tleStale());
    }

    private static Pass shift(Pass pass, Duration delta) {
        return new Pass(
                pass.riseTime().plus(delta),
                pass.riseAzimuthDeg(),
                pass.riseDirection(),
                pass.culminationTime().plus(delta),
                pass.maxElevationDeg(),
                pass.setTime().plus(delta),
                pass.setAzimuthDeg(),
                pass.setDirection(),
                pass.durationSec(),
                pass.visible(),
                pass.visibleStart() == null ? null : pass.visibleStart().plus(delta),
                pass.visibleEnd() == null ? null : pass.visibleEnd().plus(delta),
                pass.visibleDurationSec()
        );
    }
}
