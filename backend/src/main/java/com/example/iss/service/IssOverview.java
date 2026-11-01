package com.example.iss.service;

import com.example.iss.domain.IssPosition;

import java.util.List;

/**
 * The full aggregate result for GET /api/v1/iss/overview (PROMPT.md section 6).
 * Domain-shaped only; the controller maps this to the public JSON DTO + HATEOAS links.
 *
 * @param message null when nextVisiblePass is present; otherwise
 *                "No visible pass in the returned predictions" (Step 6 decision 5)
 */
public record IssOverview(
        IssPosition position,
        UpstreamMeta positionMeta,
        List<EnrichedPass> passes,
        PassesMeta passesMeta,
        EnrichedPass nextVisiblePass,
        String message,
        List<String> alerts,
        UpstreamMeta cloudMeta
) {
}
