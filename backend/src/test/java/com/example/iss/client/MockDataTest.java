package com.example.iss.client;

import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
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
 * MockData: loads the static MOCK fallbacks (position, cloud) with the re-anchoring
 * rules from PROMPT.md section 9 and Step 5 decision (f). mocks/cloud.json itself is
 * NEVER edited; its 24-hour curve is repeated to cover 240 hourly entries after
 * re-anchoring to today's 00:00 UTC.
 */
class MockDataTest {

    private JsonNode mockPositionJson;
    private JsonNode mockCloudJson;
    private JsonNode mockPassesJson;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mockPositionJson = mapper.readTree(new File("src/main/resources/mocks/position.json"));
        mockCloudJson = mapper.readTree(new File("src/main/resources/mocks/cloud.json"));
        mockPassesJson = mapper.readTree(new File("src/main/resources/mocks/passes.json"));
    }

    @Nested
    @DisplayName("loadPosition: not time-shifted")
    class LoadPositionNotTimeShifted {

        @Test
        @DisplayName("given the mock position fixture, when loaded, then lat/lon parse correctly and the timestamp is unshifted")
        void givenMockPositionFixture_whenLoaded_thenLatLonCorrectAndTimestampUnshifted() {
            IssPosition position = MockData.loadPosition(mockPositionJson);

            assertThat(position.latitude()).isEqualTo(20.0000);
            assertThat(position.longitude()).isEqualTo(0.0000);
            assertThat(position.observedAt().getEpochSecond()).isEqualTo(1793440000L);
            assertThat(position.altitudeKm()).isEqualTo(IssPosition.CONSTANT_ALTITUDE_KM);
        }
    }

    @Nested
    @DisplayName("loadCloud: re-anchored to today's 00:00 UTC, repeated to 240 entries")
    class LoadCloudReanchored {

        @Test
        @DisplayName("given a fixed clock, when cloud is loaded, then the first hourly entry is today's 00:00 UTC")
        void givenFixedClock_whenCloudLoaded_thenFirstEntryIsTodayMidnightUtc() {
            Instant fixedNow = Instant.parse("2026-11-05T14:00:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

            CloudForecast forecast = MockData.loadCloud(mockCloudJson, clock);

            Instant expectedMidnight = Instant.parse("2026-11-05T00:00:00Z");
            assertThat(forecast.hourlyTimes().get(0)).isEqualTo(expectedMidnight);
        }

        @Test
        @DisplayName("given a fixed clock, when cloud is loaded, then it covers 240 hourly entries (10 days)")
        void givenFixedClock_whenCloudLoaded_thenCovers240Entries() {
            Clock clock = Clock.fixed(Instant.parse("2026-11-05T14:00:00Z"), ZoneOffset.UTC);

            CloudForecast forecast = MockData.loadCloud(mockCloudJson, clock);

            assertThat(forecast.hourlyTimes()).hasSize(240);
            assertThat(forecast.hourlyCloudCoverPct()).hasSize(240);
        }

        @Test
        @DisplayName("given a fixed clock, when cloud is loaded, then the 24-hour cloud_cover curve repeats identically each day")
        void givenFixedClock_whenCloudLoaded_thenCurveRepeatsEachDay() {
            Clock clock = Clock.fixed(Instant.parse("2026-11-05T14:00:00Z"), ZoneOffset.UTC);

            CloudForecast forecast = MockData.loadCloud(mockCloudJson, clock);

            for (int hourOfDay = 0; hourOfDay < 24; hourOfDay++) {
                Integer day0Value = forecast.hourlyCloudCoverPct().get(hourOfDay);
                Integer day1Value = forecast.hourlyCloudCoverPct().get(hourOfDay + 24);
                assertThat(day1Value).isEqualTo(day0Value);
            }
        }

        @Test
        @DisplayName("given a fixed clock at 23:30 UTC, when the mock pass (shifted +90min) is looked up, then cloud cover is non-null and below 20")
        void givenFixedClockAt2330Utc_whenShiftedMockPassLookedUp_thenCloudBelow20() {
            Instant fixedNow = Instant.parse("2026-11-05T23:30:00Z");
            Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

            PassFeed shiftedPasses = PassMock.load(mockPassesJson, clock);
            Pass firstObservable = shiftedPasses.passes().stream()
                    .filter(Pass::isObservable)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("mock must contain an observable pass"));
            assertThat(firstObservable.visibleStart()).isEqualTo(fixedNow.plus(Duration.ofMinutes(90)));

            CloudForecast forecast = MockData.loadCloud(mockCloudJson, clock);
            Integer cloudAtPass = forecast.cloudCoverPctAt(firstObservable.visibleStart());

            assertThat(cloudAtPass).isNotNull();
            assertThat(cloudAtPass).isLessThan(20);
        }
    }
}
