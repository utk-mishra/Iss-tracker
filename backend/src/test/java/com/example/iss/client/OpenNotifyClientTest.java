package com.example.iss.client;

import com.example.iss.domain.IssPosition;
import com.example.iss.domain.VelocityEstimator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenNotifyClient: parse() is static and pure (tested directly against the real
 * fixture); fetch() is the instance "upstream callable" that calls HttpJson, parse(),
 * then VelocityEstimator - velocity is computed here, never inside parse()
 * (PROMPT.md section 4).
 */
class OpenNotifyClientTest {

    private JsonNode realFixture;

    @BeforeEach
    void setUp() throws Exception {
        File fixture = new File("src/test/resources/fixtures/open-notify-position.json");
        realFixture = new ObjectMapper().readTree(fixture);
    }

    @Nested
    @DisplayName("parse() against the real fixture")
    class ParseRealFixture {

        @Test
        @DisplayName("given the real open-notify-position.json fixture, when parsed, then lat/lon are doubles and observedAt is correct")
        void givenRealFixture_whenParsed_thenLatLonAreDoublesAndObservedAtCorrect() {
            OpenNotifyClient.ParsedPosition result = OpenNotifyClient.parse(realFixture);

            assertThat(result.latitude()).isEqualTo(-44.8096);
            assertThat(result.longitude()).isEqualTo(59.9079);
            assertThat(result.observedAt().getEpochSecond()).isEqualTo(1793535839L);
        }

        @Test
        @DisplayName("given a message other than success, when parsed, then an exception is thrown")
        void givenMessageOtherThanSuccess_whenParsed_thenExceptionThrown() {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode corrupted = realFixture.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) corrupted).put("message", "failure");

            assertThatThrownBy(() -> OpenNotifyClient.parse(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("given a latitude out of range, when parsed, then an exception is thrown")
        void givenLatitudeOutOfRange_whenParsed_thenExceptionThrown() {
            JsonNode corrupted = realFixture.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) corrupted.get("iss_position"))
                    .put("latitude", "91.0");

            assertThatThrownBy(() -> OpenNotifyClient.parse(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("given a longitude out of range, when parsed, then an exception is thrown")
        void givenLongitudeOutOfRange_whenParsed_thenExceptionThrown() {
            JsonNode corrupted = realFixture.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) corrupted.get("iss_position"))
                    .put("longitude", "-181.0");

            assertThatThrownBy(() -> OpenNotifyClient.parse(corrupted))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("fetch() orchestration")
    class FetchOrchestration {

        @Test
        @DisplayName("given a healthy upstream, when fetch is called, then it invokes VelocityEstimator exactly once and builds IssPosition with constant altitude")
        void givenHealthyUpstream_whenFetchCalled_thenInvokesVelocityEstimatorOnceWithConstantAltitude() {
            HttpJson httpJson = Mockito.mock(HttpJson.class);
            when(httpJson.getJson(any())).thenReturn(realFixture);
            VelocityEstimator velocityEstimator = Mockito.mock(VelocityEstimator.class);
            when(velocityEstimator.estimateKmh(anyDouble(), anyDouble(), any())).thenReturn(27600.0);

            OpenNotifyClient client = new OpenNotifyClient(httpJson, velocityEstimator, "http://example.invalid/iss-now.json");

            IssPosition position = client.fetch();

            verify(velocityEstimator, times(1)).estimateKmh(anyDouble(), anyDouble(), any());
            assertThat(position.altitudeKm()).isEqualTo(IssPosition.CONSTANT_ALTITUDE_KM);
            assertThat(position.velocityKmh()).isEqualTo(27600.0);
            assertThat(position.latitude()).isEqualTo(-44.8096);
            assertThat(position.longitude()).isEqualTo(59.9079);
            assertThat(position.note()).isEqualTo(IssPosition.ALTITUDE_NOTE);
        }
    }
}
