package com.example.iss.client;

import com.example.iss.domain.IssPosition;

/**
 * Adapter port for the current ISS position upstream (PROMPT.md section 4).
 * No parameters: there is exactly one "current position", independent of observer
 * location.
 */
public interface PositionClient {
    IssPosition fetch();
}
