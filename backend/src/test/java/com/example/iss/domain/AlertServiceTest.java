package com.example.iss.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.example.iss.domain.PassTestFactory.nonVisiblePass;
import static com.example.iss.domain.PassTestFactory.observablePass;
import static com.example.iss.domain.PassTestFactory.observablePassWithRiseDirection;
import static com.example.iss.domain.PassTestFactory.visiblePassWithZeroDuration;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertService.build(Pass, Double cloudPct, double magnitude, Instant now) -> List<String>
 * Exact strings, in order 1,2,3,4 (PROMPT.md sections 1, 5, 9).
 */
class AlertServiceTest {

    private static final String ALERT_1_TEMPLATE = "ISS visible soon - Check sky in %s";
    private static final String ALERT_2 = "Clear skies predicted - Excellent viewing opportunity";
    private static final String ALERT_3 = "Extended viewing opportunity";
    private static final String ALERT_4 = "Exceptionally bright pass - Easily visible";

    private AlertService alertService;
    private Instant now;

    @BeforeEach
    void setUp() {
        alertService = new AlertService();
        now = Instant.parse("2026-11-01T00:00:00Z");
    }

    @Nested
    @DisplayName("observable-pass rule")
    class ObservablePassRule {

        @Test
        @DisplayName("given a non-observable pass (visible=false), when alerts are built, then no alerts are returned")
        void givenNonObservablePassVisibleFalse_whenBuilt_thenNoAlerts() {
            Pass nonVisible = nonVisiblePass(
                    now.minusSeconds(120), 180.0,
                    now.minusSeconds(60), 50.0,
                    now.plusSeconds(60), 90.0,
                    371
            );

            List<String> alerts = alertService.build(nonVisible, 10.0, -3.0, now);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("given a visible pass with zero visible duration, when alerts are built, then no alerts are returned")
        void givenVisiblePassWithZeroVisibleDuration_whenBuilt_thenNoAlerts() {
            Instant visibleStart = now.plusSeconds(3600);
            Pass zeroDuration = visiblePassWithZeroDuration(visibleStart, 180.0, 300);

            List<String> alerts = alertService.build(zeroDuration, 10.0, -3.0, now);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("given the real zero-duration visible pass from pollux-passes-n20.json, when alerts are built, then no alerts are returned")
        void givenRealZeroDurationVisiblePassFromFixture_whenBuilt_thenNoAlerts() throws Exception {
            File fixture = new File("src/test/resources/fixtures/pollux-passes-n20.json");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(fixture);
            JsonNode zeroDurationNode = null;
            for (JsonNode passNode : root.get("passes")) {
                if (passNode.path("visible").asBoolean(false)
                        && passNode.path("visible_duration_sec").asLong(-1) == 0) {
                    zeroDurationNode = passNode;
                    break;
                }
            }
            assertThat(zeroDurationNode).as("real zero-duration visible pass must exist in the fixture").isNotNull();

            Instant visibleStart = Instant.parse(zeroDurationNode.get("visible_start").asText());
            Instant visibleEnd = Instant.parse(zeroDurationNode.get("visible_end").asText());
            long visibleDurationSec = zeroDurationNode.get("visible_duration_sec").asLong();
            Pass realZeroDurationPass = new Pass(
                    Instant.parse(zeroDurationNode.get("rise").get("time").asText()),
                    zeroDurationNode.get("rise").get("azimuth_deg").asDouble(),
                    zeroDurationNode.get("rise").get("compass").asText(),
                    Instant.parse(zeroDurationNode.get("culmination").get("time").asText()),
                    zeroDurationNode.get("culmination").get("elevation_deg").asDouble(),
                    Instant.parse(zeroDurationNode.get("set").get("time").asText()),
                    zeroDurationNode.get("set").get("azimuth_deg").asDouble(),
                    zeroDurationNode.get("set").get("compass").asText(),
                    zeroDurationNode.get("duration_sec").asLong(),
                    true, visibleStart, visibleEnd, visibleDurationSec
            );
            assertThat(realZeroDurationPass.isObservable()).isFalse();

            Instant nowForThisFixture = visibleStart.minusSeconds(3600);
            List<String> alerts = alertService.build(realZeroDurationPass, 5.0, -4.0, nowForThisFixture);

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("alert 1: ISS visible soon (exactly 2 hour boundary)")
    class Alert1TwoHourBoundary {

        @Test
        @DisplayName("given visible_start exactly now+2h, when alerts are built, then alert 1 fires with the rise compass")
        void givenVisibleStartExactlyNowPlus2h_whenBuilt_thenAlert1Fires() {
            Instant visibleStart = now.plus(Duration.ofHours(2));
            Pass pass = observablePass(visibleStart, visibleStart.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).contains(String.format(ALERT_1_TEMPLATE, "N"));
        }

        @Test
        @DisplayName("given visible_start at now+2h+1s, when alerts are built, then alert 1 does not fire")
        void givenVisibleStartNowPlus2hPlus1s_whenBuilt_thenAlert1DoesNotFire() {
            Instant visibleStart = now.plus(Duration.ofHours(2)).plusSeconds(1);
            Pass pass = observablePass(visibleStart, visibleStart.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).noneMatch(a -> a.startsWith("ISS visible soon"));
        }

        @Test
        @DisplayName("given visible_start already in the past, when alerts are built, then alert 1 does not fire")
        void givenVisibleStartInThePast_whenBuilt_thenAlert1DoesNotFire() {
            Instant visibleStart = now.minusSeconds(10);
            Pass pass = observablePass(visibleStart, visibleStart.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).noneMatch(a -> a.startsWith("ISS visible soon"));
        }

        @Test
        @DisplayName("given visible_start exactly now, when alerts are built, then alert 1 fires")
        void givenVisibleStartExactlyNow_whenBuilt_thenAlert1Fires() {
            Pass pass = observablePassWithRiseDirection(now, now.plusSeconds(120), 300, 90.0, "E");

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).contains(String.format(ALERT_1_TEMPLATE, "E"));
        }
    }

    @Nested
    @DisplayName("alert 2: clear skies (cloud cover exactly 20)")
    class Alert2CloudBoundary {

        @Test
        @DisplayName("given alert-1 condition and cloud cover exactly 20, when alerts are built, then alert 2 does not fire")
        void givenCloudExactly20_whenBuilt_thenAlert2DoesNotFire() {
            Pass pass = observablePass(now, now.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, 20.0, -1.0, now);

            assertThat(alerts).doesNotContain(ALERT_2);
        }

        @Test
        @DisplayName("given alert-1 condition and cloud cover 19.9, when alerts are built, then alert 2 fires")
        void givenCloud19_9_whenBuilt_thenAlert2Fires() {
            Pass pass = observablePass(now, now.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, 19.9, -1.0, now);

            assertThat(alerts).contains(ALERT_2);
        }

        @Test
        @DisplayName("given alert-1 condition and null cloud cover, when alerts are built, then alert 2 does not fire")
        void givenNullCloud_whenBuilt_thenAlert2DoesNotFire() {
            Pass pass = observablePass(now, now.plusSeconds(120), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).doesNotContain(ALERT_2);
        }
    }

    @Nested
    @DisplayName("alert 3: extended viewing (duration exactly 300)")
    class Alert3DurationBoundary {

        @Test
        @DisplayName("given duration_sec exactly 300, when alerts are built, then alert 3 does not fire")
        void givenDurationExactly300_whenBuilt_thenAlert3DoesNotFire() {
            Pass pass = observablePass(now.plusSeconds(7200), now.plusSeconds(7200).plusSeconds(60), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).doesNotContain(ALERT_3);
        }

        @Test
        @DisplayName("given duration_sec 301, when alerts are built, then alert 3 fires")
        void givenDuration301_whenBuilt_thenAlert3Fires() {
            Pass pass = observablePass(now.plusSeconds(7200), now.plusSeconds(7200).plusSeconds(60), 301, 0.0);

            List<String> alerts = alertService.build(pass, null, -1.0, now);

            assertThat(alerts).contains(ALERT_3);
        }
    }

    @Nested
    @DisplayName("alert 4: exceptionally bright (magnitude exactly -2.0)")
    class Alert4MagnitudeBoundary {

        @Test
        @DisplayName("given magnitude exactly -2.0, when alerts are built, then alert 4 does not fire")
        void givenMagnitudeExactlyMinus2_whenBuilt_thenAlert4DoesNotFire() {
            Pass pass = observablePass(now.plusSeconds(7200), now.plusSeconds(7200).plusSeconds(60), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -2.0, now);

            assertThat(alerts).doesNotContain(ALERT_4);
        }

        @Test
        @DisplayName("given magnitude -2.1, when alerts are built, then alert 4 fires")
        void givenMagnitudeMinus2_1_whenBuilt_thenAlert4Fires() {
            Pass pass = observablePass(now.plusSeconds(7200), now.plusSeconds(7200).plusSeconds(60), 300, 0.0);

            List<String> alerts = alertService.build(pass, null, -2.1, now);

            assertThat(alerts).contains(ALERT_4);
        }
    }

    @Nested
    @DisplayName("alert ordering")
    class AlertOrdering {

        @Test
        @DisplayName("given all four alert conditions are met, when alerts are built, then they appear in order 1,2,3,4")
        void givenAllFourConditionsMet_whenBuilt_thenOrderedOneToFour() {
            Pass pass = observablePass(now, now.plusSeconds(301), 301, 0.0);

            List<String> alerts = alertService.build(pass, 10.0, -2.1, now);

            assertThat(alerts).containsExactly(
                    String.format(ALERT_1_TEMPLATE, "N"),
                    ALERT_2,
                    ALERT_3,
                    ALERT_4
            );
        }
    }
}
