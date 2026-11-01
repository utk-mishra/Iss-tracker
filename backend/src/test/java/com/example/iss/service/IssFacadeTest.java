package com.example.iss.service;

import com.example.iss.client.CloudClient;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PositionClient;
import com.example.iss.domain.AlertService;
import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.example.iss.resilience.CircuitBreaker;
import com.example.iss.resilience.ResilientFetcher;
import com.example.iss.resilience.SingleFlight;
import com.example.iss.resilience.TtlCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IssFacade: orchestrates the three ResilientFetchers + enrichment + alerts
 * (PROMPT.md section 6, Step 6 plan). No Spring context - plain constructor wiring
 * with mocked clients, so this is fast and fully isolated from the HTTP layer.
 */
class IssFacadeTest {

    private static final double LAT = 28.6139;
    private static final double LON = 77.2090;

    private PositionClient positionClient;
    private PassesClient passesClient;
    private CloudClient cloudClient;
    private Clock clock;
    private IssFacade facade;

    private JsonNode mockPositionJson;
    private JsonNode mockPassesJson;
    private JsonNode mockCloudJson;

    @BeforeEach
    void setUp() throws Exception {
        positionClient = Mockito.mock(PositionClient.class);
        passesClient = Mockito.mock(PassesClient.class);
        cloudClient = Mockito.mock(CloudClient.class);
        clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);

        ObjectMapper mapper = new ObjectMapper();
        mockPositionJson = mapper.readTree(new File("src/main/resources/mocks/position.json"));
        mockPassesJson = mapper.readTree(new File("src/main/resources/mocks/passes.json"));
        mockCloudJson = mapper.readTree(new File("src/main/resources/mocks/cloud.json"));

        ResilientFetcher<IssPosition> positionFetcher = newFetcher(Duration.ofSeconds(5), Duration.ofHours(1));
        ResilientFetcher<PassFeed> passesFetcher = newFetcher(Duration.ofMinutes(15), Duration.ofHours(24));
        ResilientFetcher<CloudForecast> cloudFetcher = newFetcher(Duration.ofMinutes(30), Duration.ofHours(12));

        facade = new IssFacade(
                positionClient, passesClient, cloudClient,
                positionFetcher, passesFetcher, cloudFetcher,
                mockPositionJson, mockPassesJson, mockCloudJson,
                new AlertService(), clock
        );
    }

    private <T> ResilientFetcher<T> newFetcher(Duration ttl, Duration maxStale) {
        TtlCache<T> cache = new TtlCache<>(clock, ttl, maxStale, 500);
        CircuitBreaker breaker = new CircuitBreaker(clock, 3, Duration.ofSeconds(30));
        SingleFlight<T> singleFlight = new SingleFlight<>();
        return new ResilientFetcher<>(cache, breaker, singleFlight, clock, 1);
    }

    private Pass observablePass(Instant visibleStart, Instant visibleEnd, Instant setTime, long durationSec, double maxElevationDeg) {
        return new Pass(
                visibleStart.minusSeconds(60), 10.0, "N",
                visibleStart, maxElevationDeg,
                setTime, 200.0, "S",
                durationSec,
                true, visibleStart, visibleEnd,
                Duration.between(visibleStart, visibleEnd).getSeconds()
        );
    }

    @Nested
    @DisplayName("fully offline")
    class FullyOffline {

        @Test
        @DisplayName("given offline=true, when overview is requested, then all three sources are MOCK and zero network calls are made")
        void givenOffline_whenOverviewRequested_thenAllMockAndZeroNetworkCalls() {
            IssOverview overview = facade.getOverview(LAT, LON, true);

            assertThat(overview.positionMeta().source().name()).isEqualTo("MOCK");
            assertThat(overview.passesMeta().meta().source().name()).isEqualTo("MOCK");
            assertThat(overview.cloudMeta().source().name()).isEqualTo("MOCK");
            verify(positionClient, never()).fetch();
            verify(passesClient, never()).fetch(anyDouble(), anyDouble());
            verify(cloudClient, never()).fetch(anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("given offline=true, when overview is requested, then the ISS visible soon alert is present")
        void givenOffline_whenOverviewRequested_thenIssVisibleSoonAlertPresent() {
            IssOverview overview = facade.getOverview(LAT, LON, true);

            assertThat(overview.alerts()).anyMatch(a -> a.startsWith("ISS visible soon"));
        }
    }

    @Nested
    @DisplayName("dropping past passes")
    class DroppingPastPasses {

        @Test
        @DisplayName("given a pass whose set time is already past, when overview is requested, then that pass is excluded and never nextVisiblePass")
        void givenPassSetTimeInPast_whenOverviewRequested_thenExcluded() {
            Instant now = clock.instant();
            Pass pastPass = observablePass(now.minusSeconds(600), now.minusSeconds(300), now.minusSeconds(100), 400, 50.0);
            Pass futurePass = observablePass(now.plusSeconds(3600), now.plusSeconds(3900), now.plusSeconds(4000), 400, 50.0);
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(pastPass, futurePass), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(), List.of()));
            when(positionClient.fetch()).thenReturn(
                    new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            assertThat(overview.passes()).extracting(EnrichedPass::pass).doesNotContain(pastPass);
            assertThat(overview.passes()).extracting(EnrichedPass::pass).contains(futurePass);
            assertThat(overview.nextVisiblePass()).isNotNull();
            assertThat(overview.nextVisiblePass().pass()).isEqualTo(futurePass);
        }
    }

    @Nested
    @DisplayName("next visible pass selection")
    class NextVisiblePassSelection {

        @Test
        @DisplayName("given a pass whose visibleEnd is exactly now, when overview is requested, then it is excluded")
        void givenVisibleEndExactlyNow_whenOverviewRequested_thenExcluded() {
            Instant now = clock.instant();
            Pass endingNow = observablePass(now.minusSeconds(300), now, now.plusSeconds(60), 400, 50.0);
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(endingNow), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(), List.of()));
            when(positionClient.fetch()).thenReturn(new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            assertThat(overview.nextVisiblePass()).isNull();
            assertThat(overview.message()).isEqualTo("No visible pass in the returned predictions");
        }

        @Test
        @DisplayName("given a pass whose visibleEnd is one second after now, when overview is requested, then it is the nextVisiblePass")
        void givenVisibleEndOneSecondAfterNow_whenOverviewRequested_thenIncluded() {
            Instant now = clock.instant();
            Pass endingSoon = observablePass(now.minusSeconds(300), now.plusSeconds(1), now.plusSeconds(60), 400, 50.0);
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(endingSoon), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(), List.of()));
            when(positionClient.fetch()).thenReturn(new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            assertThat(overview.nextVisiblePass()).isNotNull();
            assertThat(overview.nextVisiblePass().pass()).isEqualTo(endingSoon);
            assertThat(overview.message()).isNull();
        }
    }

    @Nested
    @DisplayName("cache-key rounding")
    class CacheKeyRounding {

        @Test
        @DisplayName("given two lat/lon pairs differing only beyond the 2nd decimal, when fetched twice, then the upstream is invoked only once")
        void givenLatLonDifferingBeyond2ndDecimal_whenFetchedTwice_thenUpstreamInvokedOnce() {
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(), List.of()));
            when(positionClient.fetch()).thenReturn(
                    new IssPosition(1.0, 2.0, 408.0, 27000.0, clock.instant(), "note"));

            facade.getOverview(28.61391, 77.20899, false);
            facade.getOverview(28.61394, 77.20901, false);

            verify(passesClient, Mockito.times(1)).fetch(anyDouble(), anyDouble());
            verify(cloudClient, Mockito.times(1)).fetch(anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("given lat/lon that rounds to negative zero, when the cache key is built, then it is normalised to the same key as positive zero")
        void givenLatLonRoundingToNegativeZero_whenCacheKeyBuilt_thenNormalisedToPositiveZero() {
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(), List.of()));
            when(positionClient.fetch()).thenReturn(
                    new IssPosition(1.0, 2.0, 408.0, 27000.0, clock.instant(), "note"));

            facade.getOverview(-0.001, -0.001, false);
            facade.getOverview(0.001, 0.001, false);

            verify(passesClient, Mockito.times(1)).fetch(anyDouble(), anyDouble());
            verify(cloudClient, Mockito.times(1)).fetch(anyDouble(), anyDouble());
        }
    }

    @Nested
    @DisplayName("per-pass enrichment")
    class PerPassEnrichment {

        @Test
        @DisplayName("given an observable pass and known cloud cover, when overview is requested, then both magnitude and cloud cover are populated")
        void givenObservablePassAndKnownCloud_whenOverviewRequested_thenMagnitudeAndCloudPopulated() {
            Instant now = clock.instant();
            Instant visibleStart = now.plusSeconds(3600);
            Pass pass = observablePass(visibleStart, visibleStart.plusSeconds(300), visibleStart.plusSeconds(360), 400, 85.0);
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(pass), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(visibleStart), List.of(15)));
            when(positionClient.fetch()).thenReturn(new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            EnrichedPass enriched = overview.passes().get(0);
            assertThat(enriched.magnitude()).isEqualTo(-3.5);
            assertThat(enriched.cloudCoverPct()).isEqualTo(15);
        }

        @Test
        @DisplayName("given a non-observable pass, when overview is requested, then magnitude is null but cloud cover is still populated")
        void givenNonObservablePass_whenOverviewRequested_thenMagnitudeNullButCloudPopulated() {
            Instant now = clock.instant();
            Instant riseTime = now.plusSeconds(3600);
            Pass nonObservable = new Pass(
                    riseTime, 10.0, "N",
                    riseTime.plusSeconds(60), 15.0,
                    riseTime.plusSeconds(120), 200.0, "S",
                    120, false, null, null, 0
            );
            when(passesClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new PassFeed(List.of(nonObservable), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble()))
                    .thenReturn(new CloudForecast(List.of(riseTime), List.of(40)));
            when(positionClient.fetch()).thenReturn(new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            EnrichedPass enriched = overview.passes().get(0);
            assertThat(enriched.magnitude()).isNull();
            assertThat(enriched.cloudCoverPct()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("concurrent upstream fetches")
    class ConcurrentUpstreamFetches {

        @Test
        @DisplayName("given all three upstreams succeed, when overview is requested, then all three results are present and FRESH")
        void givenAllUpstreamsSucceed_whenOverviewRequested_thenAllFresh() {
            Instant now = clock.instant();
            when(positionClient.fetch()).thenReturn(new IssPosition(1.0, 2.0, 408.0, 27000.0, now, "note"));
            when(passesClient.fetch(anyDouble(), anyDouble())).thenReturn(new PassFeed(List.of(), 5.0, false));
            when(cloudClient.fetch(anyDouble(), anyDouble())).thenReturn(new CloudForecast(List.of(), List.of()));

            IssOverview overview = facade.getOverview(LAT, LON, false);

            assertThat(overview.positionMeta().source().name()).isEqualTo("FRESH");
            assertThat(overview.passesMeta().meta().source().name()).isEqualTo("FRESH");
            assertThat(overview.cloudMeta().source().name()).isEqualTo("FRESH");
        }
    }
}
