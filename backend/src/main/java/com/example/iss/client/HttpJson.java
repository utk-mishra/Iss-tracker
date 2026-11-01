package com.example.iss.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin java.net.http.HttpClient wrapper: GET a URI, reject non-2xx, parse the body as
 * JSON. Nothing beyond this sees raw upstream JSON (PROMPT.md section 4 - Adapter
 * pattern). Connect and request timeouts are constructor parameters (defaults 2s/3s)
 * so tests can use short values without touching production config.
 */
public final class HttpJson {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpJson() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public HttpJson(Duration connectTimeout, Duration requestTimeout) {
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * GETs {@code uri} and returns the parsed JSON body.
     *
     * @throws HttpJsonException if the response status is not 2xx
     * @throws RuntimeException  if the request fails (timeout, connection error) or the
     *                            body is not valid JSON; callers (ResilientFetcher) treat
     *                            any exception here as a generic upstream failure
     */
    public JsonNode getJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status > 299) {
                throw new HttpJsonException("Upstream returned HTTP " + status + " for " + uri);
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed for " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request interrupted for " + uri, e);
        }
    }
}
