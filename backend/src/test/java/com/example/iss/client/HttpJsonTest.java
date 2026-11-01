package com.example.iss.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HttpJson: thin java.net.http.HttpClient wrapper. Tested against the JDK's built-in
 * com.sun.net.httpserver.HttpServer on an ephemeral port - no real network calls, no
 * new dependencies, no Thread.sleep. Timeouts are constructor parameters so tests can
 * use short values (~200ms) instead of the 2s/3s production defaults.
 */
class HttpJsonTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Nested
    @DisplayName("successful responses")
    class SuccessfulResponses {

        @Test
        @DisplayName("given a 200 response with valid JSON, when fetched, then the parsed JsonNode is returned")
        void given200WithValidJson_whenFetched_thenParsedJsonNodeReturned() throws Exception {
            server.createContext("/ok", exchange -> {
                byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            HttpJson httpJson = new HttpJson(Duration.ofSeconds(2), Duration.ofSeconds(3));

            JsonNode result = httpJson.getJson(URI.create(baseUrl() + "/ok"));

            assertThat(result.get("hello").asText()).isEqualTo("world");
        }
    }

    @Nested
    @DisplayName("non-2xx responses")
    class NonTwoXxResponses {

        @Test
        @DisplayName("given a 404 response, when fetched, then an exception is thrown")
        void given404Response_whenFetched_thenExceptionThrown() throws Exception {
            server.createContext("/missing", exchange -> {
                byte[] body = "not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            HttpJson httpJson = new HttpJson(Duration.ofSeconds(2), Duration.ofSeconds(3));

            assertThatThrownBy(() -> httpJson.getJson(URI.create(baseUrl() + "/missing")))
                    .isInstanceOf(HttpJsonException.class);
        }

        @Test
        @DisplayName("given a 500 response, when fetched, then an exception is thrown")
        void given500Response_whenFetched_thenExceptionThrown() throws Exception {
            server.createContext("/error", exchange -> {
                byte[] body = "server error".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            HttpJson httpJson = new HttpJson(Duration.ofSeconds(2), Duration.ofSeconds(3));

            assertThatThrownBy(() -> httpJson.getJson(URI.create(baseUrl() + "/error")))
                    .isInstanceOf(HttpJsonException.class);
        }
    }

    @Nested
    @DisplayName("malformed body")
    class MalformedBody {

        @Test
        @DisplayName("given a 200 response with invalid JSON, when fetched, then an exception is thrown")
        void given200WithInvalidJson_whenFetched_thenExceptionThrown() throws Exception {
            server.createContext("/badjson", exchange -> {
                byte[] body = "{not valid json".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            HttpJson httpJson = new HttpJson(Duration.ofSeconds(2), Duration.ofSeconds(3));

            assertThatThrownBy(() -> httpJson.getJson(URI.create(baseUrl() + "/badjson")))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("request timeout")
    class RequestTimeout {

        @Test
        @DisplayName("given a server that never responds, when fetched with a short request timeout, then it throws within that timeout")
        void givenServerNeverResponds_whenFetchedWithShortTimeout_thenThrowsWithinTimeout() throws Exception {
            CountDownLatch releaseHandler = new CountDownLatch(1);
            server.createContext("/hang", exchange -> {
                try {
                    releaseHandler.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
                exchange.close();
            });
            server.start();
            HttpJson httpJson = new HttpJson(Duration.ofMillis(200), Duration.ofMillis(200));

            try {
                assertThatThrownBy(() -> httpJson.getJson(URI.create(baseUrl() + "/hang")))
                        .isInstanceOf(Exception.class);
            } finally {
                releaseHandler.countDown();
            }
        }
    }

    @Nested
    @DisplayName("timeout configuration")
    class TimeoutConfiguration {

        @Test
        @DisplayName("given default constructor values, when connect timeout is inspected, then it is 2 seconds")
        void givenDefaultValues_whenConnectTimeoutInspected_thenIs2Seconds() {
            HttpJson httpJson = new HttpJson();

            assertThat(httpJson.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("given default constructor values, when request timeout is inspected, then it is 3 seconds")
        void givenDefaultValues_whenRequestTimeoutInspected_thenIs3Seconds() {
            HttpJson httpJson = new HttpJson();

            assertThat(httpJson.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
        }
    }
}
