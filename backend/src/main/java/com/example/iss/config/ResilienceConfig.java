package com.example.iss.config;

import com.example.iss.client.CloudClient;
import com.example.iss.client.HttpJson;
import com.example.iss.client.OpenMeteoClient;
import com.example.iss.client.OpenNotifyClient;
import com.example.iss.client.PassesClient;
import com.example.iss.client.PolluxClient;
import com.example.iss.client.PositionClient;
import com.example.iss.domain.AlertService;
import com.example.iss.domain.CloudForecast;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.PassFeed;
import com.example.iss.domain.VelocityEstimator;
import com.example.iss.resilience.CircuitBreaker;
import com.example.iss.resilience.ResilientFetcher;
import com.example.iss.resilience.SingleFlight;
import com.example.iss.resilience.TtlCache;
import com.example.iss.service.IssFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Single place that wires the resilience layer, upstream clients, mock data, and the
 * IssFacade itself (PROMPT.md section 4: constructor injection, SOLID). One Clock bean
 * is shared everywhere so tests and production share the same time source abstraction.
 */
@Configuration
@EnableConfigurationProperties(UpstreamProperties.class)
public class ResilienceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public HttpJson httpJson(UpstreamProperties props) {
        Duration requestTimeout = Duration.ofSeconds(props.getHttp().getTimeoutSeconds());
        return new HttpJson(Duration.ofSeconds(2), requestTimeout);
    }

    @Bean
    public VelocityEstimator velocityEstimator() {
        return new VelocityEstimator();
    }

    @Bean
    public AlertService alertService() {
        return new AlertService();
    }

    @Bean
    public PositionClient positionClient(HttpJson httpJson, VelocityEstimator velocityEstimator,
                                          UpstreamProperties props) {
        return new OpenNotifyClient(httpJson, velocityEstimator, props.getUpstream().getOpenNotify().getUrl());
    }

    @Bean
    public PassesClient passesClient(HttpJson httpJson, UpstreamProperties props) {
        return new PolluxClient(httpJson, props.getUpstream().getPollux().getUrl(),
                props.getUpstream().getPollux().getPassCount());
    }

    @Bean
    public CloudClient cloudClient(HttpJson httpJson, UpstreamProperties props) {
        return new OpenMeteoClient(httpJson, props.getUpstream().getOpenMeteo().getUrl());
    }

    @Bean
    public ResilientFetcher<IssPosition> positionFetcher(Clock clock, UpstreamProperties props) {
        var window = props.getCache().getPosition();
        return newFetcher(clock, props, window.getTtlSeconds(), window.getMaxStaleSeconds());
    }

    @Bean
    public ResilientFetcher<PassFeed> passesFetcher(Clock clock, UpstreamProperties props) {
        var window = props.getCache().getPasses();
        return newFetcher(clock, props, window.getTtlSeconds(), window.getMaxStaleSeconds());
    }

    @Bean
    public ResilientFetcher<CloudForecast> cloudFetcher(Clock clock, UpstreamProperties props) {
        var window = props.getCache().getCloud();
        return newFetcher(clock, props, window.getTtlSeconds(), window.getMaxStaleSeconds());
    }

    private <T> ResilientFetcher<T> newFetcher(Clock clock, UpstreamProperties props,
                                                long ttlSeconds, long maxStaleSeconds) {
        TtlCache<T> cache = new TtlCache<>(clock, Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(maxStaleSeconds), props.getCache().getMaxEntries());
        CircuitBreaker breaker = new CircuitBreaker(clock, props.getCircuitBreaker().getFailureThreshold(),
                Duration.ofSeconds(props.getCircuitBreaker().getCooldownSeconds()));
        SingleFlight<T> singleFlight = new SingleFlight<>();
        return new ResilientFetcher<>(cache, breaker, singleFlight, clock, props.getHttp().getRetryCount());
    }

    @Bean
    public JsonNode mockPositionJson() throws IOException {
        return readMock("mocks/position.json");
    }

    @Bean
    public JsonNode mockPassesJson() throws IOException {
        return readMock("mocks/passes.json");
    }

    @Bean
    public JsonNode mockCloudJson() throws IOException {
        return readMock("mocks/cloud.json");
    }

    private JsonNode readMock(String classpathLocation) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    @Bean
    public IssFacade issFacade(PositionClient positionClient, PassesClient passesClient, CloudClient cloudClient,
                                ResilientFetcher<IssPosition> positionFetcher,
                                ResilientFetcher<PassFeed> passesFetcher,
                                ResilientFetcher<CloudForecast> cloudFetcher,
                                @Autowired JsonNode mockPositionJson,
                                @Autowired JsonNode mockPassesJson,
                                @Autowired JsonNode mockCloudJson,
                                AlertService alertService, Clock clock) {
        return new IssFacade(positionClient, passesClient, cloudClient,
                positionFetcher, passesFetcher, cloudFetcher,
                mockPositionJson, mockPassesJson, mockCloudJson, alertService, clock);
    }
}
