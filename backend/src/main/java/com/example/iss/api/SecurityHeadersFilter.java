package com.example.iss.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Applies the fixed security header set (Step 6 decision 3) to every response, and
 * Cache-Control: no-store to /api/** responses only (API data is always fresh-checked
 * through the resilience layer; it must never be cached by intermediaries or browsers).
 */
public final class SecurityHeadersFilter extends HttpFilter {

    private static final String CSP =
            "default-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; "
                    + "connect-src 'self'; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'";

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(self)");
        response.setHeader("Content-Security-Policy", CSP);

        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
        }

        chain.doFilter(request, response);
    }
}
