package com.example.iss.service;

import com.example.iss.client.CloudClient;
import com.example.iss.client.MockData;
import com.example.iss.client.PassMock;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PositionClient;
import com.example.iss.domain.AlertService;
import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.MagnitudeCalculator;
import com.example.iss.domain.Pass;
import com.example.iss.domain.PassFeed;
import com.example.iss.resilience.ResilientFetcher;
import com.example.iss.resilience.Result;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates the three ResilientFetchers, drops past passes, selects the next
 * observable pass, enriches every pass with cloud cover and (for observable passes)
 * magnitude, and builds alerts for the next visible pass only (PROMPT.md section 6).
 *
 * <p>Cache keys: a fixed constant for position (no lat/lon dimension); lat/lon rounded
 * to 2 decimals (negative zero normalised to positive zero) for passes/cloud, with that
 * SAME rounded pair passed to the clients, so a cache hit always matches the upstream
 * query it was derived from (Step 6 carried-over rule from Step 5).
 *
 * <p>The three upstream fetches run concurrently on virtual threads
 * ({@link Executors#newVirtualThreadPerTaskExecutor()}, no new dependency); each fetch's
 * own fallback chain lives entirely inside its {@link ResilientFetcher}, so running them
 * concurrently only affects wall-clock latency, never behaviour.
 */
public final class IssFacade {

    private static final String POSITION_CACHE_KEY = "position";
    private static final String NO_VISIBLE_PASS_MESSAGE = "No visible pass in the returned predictions";

    private final PositionClient positionClient;
    private final PassesClient passesClient;
    private final CloudClient cloudClient;
    private final ResilientFetcher<IssPosition> positionFetcher;
    private final ResilientFetcher<PassFeed> passesFetcher;
    private final ResilientFetcher<CloudForecast> cloudFetcher;
    private final JsonNode mockPositionJson;
    private final JsonNode mockPassesJson;
    private final JsonNode mockCloudJson;
    private final AlertService alertService;
    private final Clock clock;

    public IssFacade(PositionClient positionClient, PassesClient passesClient, CloudClient cloudClient,
                      ResilientFetcher<IssPosition> positionFetcher, ResilientFetcher<PassFeed> passesFetcher,
                      ResilientFetcher<CloudForecast> cloudFetcher,
                      JsonNode mockPositionJson, JsonNode mockPassesJson, JsonNode mockCloudJson,
                      AlertService alertService, Clock clock) {
        this.positionClient = Objects.requireNonNull(positionClient, "positionClient");
        this.passesClient = Objects.requireNonNull(passesClient, "passesClient");
        this.cloudClient = Objects.requireNonNull(cloudClient, "cloudClient");
        this.positionFetcher = Objects.requireNonNull(positionFetcher, "positionFetcher");
        this.passesFetcher = Objects.requireNonNull(passesFetcher, "passesFetcher");
        this.cloudFetcher = Objects.requireNonNull(cloudFetcher, "cloudFetcher");
        this.mockPositionJson = Objects.requireNonNull(mockPositionJson, "mockPositionJson");
        this.mockPassesJson = Objects.requireNonNull(mockPassesJson, "mockPassesJson");
        this.mockCloudJson = Objects.requireNonNull(mockCloudJson, "mockCloudJson");
        this.alertService = Objects.requireNonNull(alertService, "alertService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Rounds to 2 decimals and normalises negative zero to positive zero. */
    static double roundCoordinate(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        return rounded == 0.0 ? 0.0 : rounded;
    }

    static String passesKey(double roundedLat, double roundedLon) {
        return String.format(Locale.ROOT, "%.2f,%.2f", roundedLat, roundedLon);
    }

    public IssOverview getOverview(double lat, double lon, boolean offline) {
        double roundedLat = roundCoordinate(lat);
        double roundedLon = roundCoordinate(lon);
        String key = passesKey(roundedLat, roundedLon);

        FetchResults fetches = fetchAllConcurrently(roundedLat, roundedLon, key, offline);

        Instant now = clock.instant();
        List<Pass> remainingPasses = dropPastPasses(fetches.passes.data().passes(), now);
        Pass nextVisible = findNextVisiblePass(remainingPasses, now);

        List<EnrichedPass> enrichedPasses = enrichPasses(remainingPasses, fetches.cloud.data());
        EnrichedPass enrichedNextVisible = nextVisible == null ? null
                : enrichedPasses.stream().filter(p -> p.pass() == nextVisible).findFirst().orElse(null);

        List<String> alerts;
        String message;
        if (enrichedNextVisible != null) {
            Double cloudPct = enrichedNextVisible.cloudCoverPct() == null ? null
                    : enrichedNextVisible.cloudCoverPct().doubleValue();
            double magnitude = enrichedNextVisible.magnitude();
            alerts = alertService.build(nextVisible, cloudPct, magnitude, now);
            message = null;
        } else {
            alerts = List.of();
            message = NO_VISIBLE_PASS_MESSAGE;
        }

        return new IssOverview(
                fetches.position.data(),
                UpstreamMeta.from(fetches.position),
                enrichedPasses,
                new PassesMeta(UpstreamMeta.from(fetches.passes), fetches.passes.data().tleAgeHours(),
                        fetches.passes.data().tleStale()),
                enrichedNextVisible,
                message,
                alerts,
                UpstreamMeta.from(fetches.cloud)
        );
    }

    public PositionResult getPosition(boolean offline) {
        Result<IssPosition> result = positionFetcher.fetch(POSITION_CACHE_KEY, positionClient::fetch,
                () -> MockData.loadPosition(mockPositionJson), offline);
        return new PositionResult(result.data(), UpstreamMeta.from(result));
    }

    public PassesResult getPasses(double lat, double lon, boolean offline) {
        double roundedLat = roundCoordinate(lat);
        double roundedLon = roundCoordinate(lon);
        String key = passesKey(roundedLat, roundedLon);

        Result<PassFeed> passesResult = passesFetcher.fetch(key, () -> passesClient.fetch(roundedLat, roundedLon),
                () -> PassMock.load(mockPassesJson, clock), offline);
        Result<CloudForecast> cloudResult = cloudFetcher.fetch(key, () -> cloudClient.fetch(roundedLat, roundedLon),
                () -> MockData.loadCloud(mockCloudJson, clock), offline);

        Instant now = clock.instant();
        List<Pass> remaining = dropPastPasses(passesResult.data().passes(), now);
        List<EnrichedPass> enriched = enrichPasses(remaining, cloudResult.data());

        return new PassesResult(
                enriched,
                new PassesMeta(UpstreamMeta.from(passesResult), passesResult.data().tleAgeHours(),
                        passesResult.data().tleStale()),
                UpstreamMeta.from(cloudResult)
        );
    }

    private record FetchResults(Result<IssPosition> position, Result<PassFeed> passes, Result<CloudForecast> cloud) {
    }

    private FetchResults fetchAllConcurrently(double roundedLat, double roundedLon, String key, boolean offline) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Result<IssPosition>> positionFuture = executor.submit(() ->
                    positionFetcher.fetch(POSITION_CACHE_KEY, positionClient::fetch,
                            () -> MockData.loadPosition(mockPositionJson), offline));
            Future<Result<PassFeed>> passesFuture = executor.submit(() ->
                    passesFetcher.fetch(key, () -> passesClient.fetch(roundedLat, roundedLon),
                            () -> PassMock.load(mockPassesJson, clock), offline));
            Future<Result<CloudForecast>> cloudFuture = executor.submit(() ->
                    cloudFetcher.fetch(key, () -> cloudClient.fetch(roundedLat, roundedLon),
                            () -> MockData.loadCloud(mockCloudJson, clock), offline));

            return new FetchResults(positionFuture.get(), passesFuture.get(), cloudFuture.get());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch ISS data concurrently", e);
        }
    }

    private List<Pass> dropPastPasses(List<Pass> passes, Instant now) {
        List<Pass> remaining = new ArrayList<>();
        for (Pass pass : passes) {
            if (pass.setTime().isAfter(now)) {
                remaining.add(pass);
            }
        }
        return remaining;
    }

    private Pass findNextVisiblePass(List<Pass> passes, Instant now) {
        for (Pass pass : passes) {
            if (pass.isObservable() && pass.visibleEnd().isAfter(now)) {
                return pass;
            }
        }
        return null;
    }

    private List<EnrichedPass> enrichPasses(List<Pass> passes, CloudForecast cloudForecast) {
        List<EnrichedPass> enriched = new ArrayList<>();
        for (Pass pass : passes) {
            Instant cloudLookupTime = pass.visible() && pass.visibleStart() != null
                    ? pass.visibleStart() : pass.riseTime();
            Integer cloudCoverPct = cloudForecast.cloudCoverPctAt(cloudLookupTime);
            Double magnitude = pass.isObservable()
                    ? MagnitudeCalculator.fromMaxElevationDeg(pass.maxElevationDeg()) : null;
            enriched.add(new EnrichedPass(pass, magnitude, cloudCoverPct));
        }
        return enriched;
    }
}
