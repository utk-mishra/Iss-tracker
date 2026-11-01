package com.example.iss.client;

import com.example.iss.domain.CloudForecast;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenMeteoClient: static pure parse(JsonNode) against the REAL open-meteo-cloud.json
 * fixture (240 hourly entries). Negative-path rules (null value, mismatched arrays,
 * non-hourly step) are tested via in-memory corrupted clones (PROMPT.md section 3.3).
 */
class OpenMeteoClientTest {

    private JsonNode realFixture;

    @BeforeEach
    void setUp() throws Exception {
        realFixture = new ObjectMapper().readTree(new File("src/test/resources/fixtures/open-meteo-cloud.json"));
    }

    @Nested
    @DisplayName("parse() against the real fixture")
    class ParseRealFixture {

        @Test
        @DisplayName("given the real fixture, when parsed, then 240 hourly entries with no-Z times are returned")
        void givenRealFixture_whenParsed_then240EntriesWithNoZTimes() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);

            assertThat(forecast.hourlyTimes()).hasSize(240);
            assertThat(forecast.hourlyCloudCoverPct()).hasSize(240);
            assertThat(forecast.hourlyTimes().get(0)).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        }

        @Test
        @DisplayName("given the real fixture, when parsed, then the step between consecutive hours is exactly 1 hour")
        void givenRealFixture_whenParsed_thenStepIsExactlyOneHour() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);

            for (int i = 1; i < forecast.hourlyTimes().size(); i++) {
                long diffSeconds = forecast.hourlyTimes().get(i).getEpochSecond()
                        - forecast.hourlyTimes().get(i - 1).getEpochSecond();
                assertThat(diffSeconds).isEqualTo(3600);
            }
        }
    }

    @Nested
    @DisplayName("cloudCoverPctAt lookup against the real fixture")
    class CloudCoverLookup {

        @Test
        @DisplayName("given an exact on-the-hour instant, when looked up, then the matching value is returned")
        void givenExactOnTheHourInstant_whenLookedUp_thenMatchingValueReturned() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);

            Integer result = forecast.cloudCoverPctAt(Instant.parse("2026-11-01T07:00:00Z"));

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("given an instant just past the LAST hour in the fixture, when looked up, then null is returned")
        void givenInstantJustPastLastHour_whenLookedUp_thenNull() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);
            Instant lastHour = forecast.hourlyTimes().get(forecast.hourlyTimes().size() - 1);

            Integer result = forecast.cloudCoverPctAt(lastHour.plusSeconds(3601));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("given an instant before the FIRST hour in the fixture, when looked up, then null is returned")
        void givenInstantBeforeFirstHour_whenLookedUp_thenNull() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);
            Instant firstHour = forecast.hourlyTimes().get(0);

            Integer result = forecast.cloudCoverPctAt(firstHour.minusSeconds(1));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("given an instant 29 minutes past an hour, when looked up, then it rounds down to that hour")
        void given29MinutesPastHour_whenLookedUp_thenRoundsDown() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);

            Integer result = forecast.cloudCoverPctAt(Instant.parse("2026-11-01T07:29:00Z"));

            assertThat(result).isEqualTo(forecast.cloudCoverPctAt(Instant.parse("2026-11-01T07:00:00Z")));
        }

        @Test
        @DisplayName("given an instant exactly 30 minutes past an hour, when looked up, then it rounds up to the next hour (round-half-up)")
        void given30MinutesPastHour_whenLookedUp_thenRoundsUp() {
            CloudForecast forecast = OpenMeteoClient.parse(realFixture);

            Integer result = forecast.cloudCoverPctAt(Instant.parse("2026-11-01T07:30:00Z"));

            assertThat(result).isEqualTo(forecast.cloudCoverPctAt(Instant.parse("2026-11-01T08:00:00Z")));
        }
    }

    @Nested
    @DisplayName("null cloud values (corrupted clone of the real fixture)")
    class NullCloudValues {

        @Test
        @DisplayName("given an hour with a null cloud_cover value, when parsed, then that entry is null")
        void givenHourWithNullCloudCover_whenParsed_thenEntryIsNull() {
            JsonNode corrupted = realFixture.deepCopy();
            ArrayNode cloudCoverArray = (ArrayNode) corrupted.get("hourly").get("cloud_cover");
            cloudCoverArray.set(5, cloudCoverArray.nullNode());

            CloudForecast forecast = OpenMeteoClient.parse(corrupted);

            assertThat(forecast.hourlyCloudCoverPct().get(5)).isNull();
        }

        @Test
        @DisplayName("given an hour with a null cloud_cover value, when looked up via cloudCoverPctAt, then null is returned")
        void givenHourWithNullCloudCover_whenLookedUp_thenNullReturned() {
            JsonNode corrupted = realFixture.deepCopy();
            ArrayNode cloudCoverArray = (ArrayNode) corrupted.get("hourly").get("cloud_cover");
            cloudCoverArray.set(5, cloudCoverArray.nullNode());

            CloudForecast forecast = OpenMeteoClient.parse(corrupted);
            Instant hourFive = forecast.hourlyTimes().get(5);

            assertThat(forecast.cloudCoverPctAt(hourFive)).isNull();
        }
    }

    @Nested
    @DisplayName("malformed series (corrupted clone of the real fixture)")
    class MalformedSeries {

        @Test
        @DisplayName("given mismatched time/cloud_cover array lengths, when parsed, then an exception is thrown")
        void givenMismatchedArrayLengths_whenParsed_thenExceptionThrown() {
            JsonNode corrupted = realFixture.deepCopy();
            ArrayNode cloudCoverArray = (ArrayNode) corrupted.get("hourly").get("cloud_cover");
            cloudCoverArray.remove(cloudCoverArray.size() - 1);

            assertThatThrownBy(() -> OpenMeteoClient.parse(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("given a non-1-hour gap between two consecutive times, when parsed, then an exception is thrown")
        void givenNonOneHourGap_whenParsed_thenExceptionThrown() {
            JsonNode corrupted = realFixture.deepCopy();
            ArrayNode timeArray = (ArrayNode) corrupted.get("hourly").get("time");
            timeArray.set(1, timeArray.textNode("2026-11-01T03:00"));

            assertThatThrownBy(() -> OpenMeteoClient.parse(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("URL building")
    class UrlBuilding {

        @Test
        @DisplayName("given lat/lon, when the URL is built, then it includes hourly=cloud_cover, timezone=UTC, forecast_days=10 and locale-independent coordinates")
        void givenLatLon_whenUrlBuilt_thenIncludesRequiredParamsAndLocaleIndependentCoordinates() {
            OpenMeteoClient client = new OpenMeteoClient(new HttpJson(), "https://api.open-meteo.com/v1/forecast");

            String url = client.buildUrl(28.6139, 77.2090);

            assertThat(url).contains("hourly=cloud_cover");
            assertThat(url).contains("timezone=UTC");
            assertThat(url).contains("forecast_days=10");
            assertThat(url).contains("latitude=28.6139");
            assertThat(url).contains("longitude=77.2090");
        }

        @Test
        @DisplayName("given the default system locale uses comma decimals, when the URL is built, then it still uses a dot")
        void givenCommaDecimalLocale_whenUrlBuilt_thenStillUsesDot() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                OpenMeteoClient client = new OpenMeteoClient(new HttpJson(), "https://api.open-meteo.com/v1/forecast");

                String url = client.buildUrl(28.6139, 77.2090);

                assertThat(url).contains("latitude=28.6139");
                assertThat(url).doesNotContain("28,6139");
            } finally {
                Locale.setDefault(original);
            }
        }
    }
}
