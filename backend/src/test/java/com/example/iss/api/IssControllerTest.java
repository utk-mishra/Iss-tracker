package com.example.iss.api;

import com.example.iss.client.CloudClient;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PositionClient;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.PassFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IssController: full Spring context + MockMvc. Upstream clients are mocked so no real
 * network calls happen; the resilience layer still runs (cache miss -> client -> FRESH).
 */
@SpringBootTest
@AutoConfigureMockMvc
class IssControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionClient positionClient;
    @MockBean
    private PassesClient passesClient;
    @MockBean
    private CloudClient cloudClient;

    @BeforeEach
    void setUp() {
        when(positionClient.fetch()).thenReturn(
                new IssPosition(10.0, 20.0, 408.0, 27000.0, Instant.parse("2026-11-01T00:00:00Z"), "note"));
        when(passesClient.fetch(anyDouble(), anyDouble())).thenReturn(new PassFeed(List.of(), 5.0, false));
        when(cloudClient.fetch(anyDouble(), anyDouble())).thenReturn(new CloudForecast(List.of(), List.of()));
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("given lat=91, when overview is requested, then 400 is returned with a clean JSON error body")
        void givenLat91_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "91").param("lon", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("given lon=181, when overview is requested, then 400 is returned")
        void givenLon181_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "0").param("lon", "181"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given lat=NaN, when overview is requested, then 400 is returned")
        void givenLatNaN_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "NaN").param("lon", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given lat=Infinity, when overview is requested, then 400 is returned")
        void givenLatInfinity_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "Infinity").param("lon", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given a missing lat parameter, when overview is requested, then 400 is returned")
        void givenMissingLat_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lon", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given a non-numeric lat parameter, when overview is requested, then 400 is returned")
        void givenNonNumericLat_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "abc").param("lon", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given offline is not exactly 'true' or 'false', when overview is requested, then 400 is returned")
        void givenInvalidOfflineValue_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "0").param("lon", "0").param("offline", "yes"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("given offline=true, when overview is requested, then 200 is returned")
        void givenOfflineTrue_whenOverviewRequested_then200() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "0").param("lon", "0").param("offline", "true"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("given a missing lon parameter, when overview is requested, then 400 is returned with a clean JSON error body")
        void givenMissingLon_whenOverviewRequested_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("exception handling (bug fix: catch-all handler must not swallow Spring MVC's own exceptions)")
    class ExceptionHandling {

        @Test
        @DisplayName("given an unknown URL under /api, when requested, then 404 is returned with a clean JSON error body")
        void givenUnknownApiUrl_whenRequested_then404() throws Exception {
            mockMvc.perform(get("/api/v1/iss/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("given a POST to the overview endpoint (wrong method), when requested, then 405 is returned with an Allow header and a clean JSON error body")
        void givenPostToOverview_whenRequested_then405WithAllowHeader() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/iss/overview").param("lat", "0").param("lon", "0"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Allow"))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("given an unexpected RuntimeException occurs outside the resilience fallback chain, when overview is requested, then 500 is returned with the generic message and no leaked details")
        void givenUnexpectedRuntimeExceptionOutsideResilience_whenOverviewRequested_then500GenericNoLeak() throws Exception {
            // A client-thrown exception alone can NEVER surface as a 500: ResilientFetcher
            // (Step 3) catches it and falls back to STALE/MOCK by design, so the controller
            // would correctly still see 200. To exercise the generic-500 path we need a
            // failure OUTSIDE that fallback-protected fetch - e.g. a malformed upstream
            // response that breaks IssFacade's post-fetch enrichment. Returning null here
            // (a buggy/unexpected upstream client implementation) triggers a real
            // NullPointerException in IssFacade.enrichPasses -> CloudForecast.cloudCoverPctAt,
            // which is exactly the kind of "genuinely unexpected failure" the generic
            // handler exists for.
            Instant now = Instant.now();
            com.example.iss.domain.Pass pass = new com.example.iss.domain.Pass(
                    now.plusSeconds(3600), 10.0, "N",
                    now.plusSeconds(3660), 50.0,
                    now.plusSeconds(3900), 200.0, "S",
                    300, true, now.plusSeconds(3660), now.plusSeconds(3900), 240
            );
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(pass), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble())).thenReturn(null);

            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "33.0").param("lon", "44.0"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal server error"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("NullPointerException"))));
        }
    }

    @Nested
    @DisplayName("overview happy path")
    class OverviewHappyPath {

        @Test
        @DisplayName("given valid lat/lon, when overview is requested, then 200 with position, meta and links")
        void givenValidLatLon_whenOverviewRequested_then200WithExpectedShape() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "28.6139").param("lon", "77.2090"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.position.latitude").value(10.0))
                    .andExpect(jsonPath("$.meta.position.source").value("FRESH"))
                    .andExpect(jsonPath("$._links.self").exists())
                    .andExpect(jsonPath("$._links.position").exists())
                    .andExpect(jsonPath("$._links.passes").exists())
                    .andExpect(jsonPath("$._links.health").exists())
                    .andExpect(jsonPath("$._links.docs").exists());
        }

        @Test
        @DisplayName("given no passes, when overview is requested, then message states no visible pass")
        void givenNoPasses_whenOverviewRequested_thenNoVisiblePassMessage() throws Exception {
            mockMvc.perform(get("/api/v1/iss/overview").param("lat", "28.6139").param("lon", "77.2090"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("No visible pass in the returned predictions"))
                    .andExpect(jsonPath("$.nextVisiblePass").doesNotExist());
        }
    }

    @Nested
    @DisplayName("position endpoint")
    class PositionEndpoint {

        @Test
        @DisplayName("given a request, when position is fetched, then 200 with position and links")
        void givenRequest_whenPositionFetched_then200() throws Exception {
            mockMvc.perform(get("/api/v1/iss/position"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.position.latitude").value(10.0))
                    .andExpect(jsonPath("$._links.self").exists());
        }
    }

    @Nested
    @DisplayName("passes endpoint")
    class PassesEndpoint {

        @Test
        @DisplayName("given valid lat/lon, when passes are fetched, then 200 with passes array and meta")
        void givenValidLatLon_whenPassesFetched_then200() throws Exception {
            mockMvc.perform(get("/api/v1/iss/passes").param("lat", "28.6139").param("lon", "77.2090"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passes").isArray())
                    .andExpect(jsonPath("$.meta.passes").exists())
                    .andExpect(jsonPath("$.meta.cloud").exists());
        }

        @Test
        @DisplayName("given lat=91, when passes are fetched, then 400 is returned")
        void givenLat91_whenPassesFetched_then400() throws Exception {
            mockMvc.perform(get("/api/v1/iss/passes").param("lat", "91").param("lon", "0"))
                    .andExpect(status().isBadRequest());
        }
    }
}
