package com.example.iss.client;

import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PassMock: time-shifts the static mocks/passes.json fixture so the first OBSERVABLE
 * pass starts ~90 minutes from "now" (per the injected Clock), per PROMPT.md section 9
 * and Step 5 decision (f). Uses PolluxClient.parse() to reuse the real parser (the mock
 * is schema-identical by design).
 */
class PassMockTest {

    private JsonNode mockPassesJson;

    @BeforeEach
    void setUp() throws Exception {
        mockPassesJson = new ObjectMapper().readTree(new File("src/main/resources/mocks/passes.json"));
    }

    @Nested
    @DisplayName("time shifting anchored on the first observable pass")
    class TimeShiftingAnchoredOnFirstObservablePass {

        @Test
        @DisplayName("given a fixed clock, when loaded, then the first observable pass's visible_start is exactly now + 90 minutes")
        void givenFixedClock_whenLoaded_thenFirstObservablePassStartsAt90MinutesFromNow() {
            Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

            PassFeed shifted = PassMock.load(mockPassesJson, clock);

            Pass firstObservable = shifted.passes().stream()
                    .filter(Pass::isObservable)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("mock must contain an observable pass"));
            assertThat(firstObservable.visibleStart()).isEqualTo(fixedNow.plus(Duration.ofMinutes(90)));
        }

        @Test
        @DisplayName("given a fixed clock, when loaded, then all time fields of every pass are shifted by the same delta")
        void givenFixedClock_whenLoaded_thenAllTimeFieldsShiftedBySameDelta() {
            Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
            PassFeed original = PolluxClient.parse(mockPassesJson);

            PassFeed shifted = PassMock.load(mockPassesJson, clock);

            Pass firstOriginalObservable = original.passes().stream()
                    .filter(Pass::isObservable).findFirst().orElseThrow();
            Pass firstShiftedObservable = shifted.passes().stream()
                    .filter(Pass::isObservable).findFirst().orElseThrow();
            Duration expectedDelta = Duration.between(firstOriginalObservable.visibleStart(),
                    firstShiftedObservable.visibleStart());

            for (int i = 0; i < original.passes().size(); i++) {
                Pass orig = original.passes().get(i);
                Pass shift = shifted.passes().get(i);
                assertThat(shift.riseTime()).isEqualTo(orig.riseTime().plus(expectedDelta));
                assertThat(shift.culminationTime()).isEqualTo(orig.culminationTime().plus(expectedDelta));
                assertThat(shift.setTime()).isEqualTo(orig.setTime().plus(expectedDelta));
                if (orig.visibleStart() != null) {
                    assertThat(shift.visibleStart()).isEqualTo(orig.visibleStart().plus(expectedDelta));
                    assertThat(shift.visibleEnd()).isEqualTo(orig.visibleEnd().plus(expectedDelta));
                }
            }
        }

        @Test
        @DisplayName("given a fixed clock, when loaded, then non-time fields (duration, elevation, azimuth, directions, visible flags) are unchanged")
        void givenFixedClock_whenLoaded_thenNonTimeFieldsUnchanged() {
            Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
            PassFeed original = PolluxClient.parse(mockPassesJson);

            PassFeed shifted = PassMock.load(mockPassesJson, clock);

            assertThat(shifted.passes()).hasSameSizeAs(original.passes());
            for (int i = 0; i < original.passes().size(); i++) {
                Pass orig = original.passes().get(i);
                Pass shift = shifted.passes().get(i);
                assertThat(shift.durationSec()).isEqualTo(orig.durationSec());
                assertThat(shift.maxElevationDeg()).isEqualTo(orig.maxElevationDeg());
                assertThat(shift.riseAzimuthDeg()).isEqualTo(orig.riseAzimuthDeg());
                assertThat(shift.setAzimuthDeg()).isEqualTo(orig.setAzimuthDeg());
                assertThat(shift.riseDirection()).isEqualTo(orig.riseDirection());
                assertThat(shift.setDirection()).isEqualTo(orig.setDirection());
                assertThat(shift.visible()).isEqualTo(orig.visible());
                assertThat(shift.visibleDurationSec()).isEqualTo(orig.visibleDurationSec());
            }
        }

        @Test
        @DisplayName("given a fixed clock, when loaded, then tle metadata is preserved unchanged")
        void givenFixedClock_whenLoaded_thenTleMetadataPreserved() {
            Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
            PassFeed original = PolluxClient.parse(mockPassesJson);

            PassFeed shifted = PassMock.load(mockPassesJson, clock);

            assertThat(shifted.tleAgeHours()).isEqualTo(original.tleAgeHours());
            assertThat(shifted.tleStale()).isEqualTo(original.tleStale());
        }
    }
}
