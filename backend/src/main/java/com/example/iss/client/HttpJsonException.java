package com.example.iss.client;

/**
 * Thrown by {@link HttpJson} for any non-2xx response. Treated as a generic upstream
 * failure by ResilientFetcher (Step 3: "catches all exceptions"), same as a timeout
 * or a parse error.
 */
public class HttpJsonException extends RuntimeException {

    public HttpJsonException(String message) {
        super(message);
    }
}
