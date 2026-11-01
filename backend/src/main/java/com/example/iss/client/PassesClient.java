package com.example.iss.client;

import com.example.iss.domain.PassFeed;

/**
 * Adapter port for pass predictions, observer-location dependent (PROMPT.md section 4).
 */
public interface PassesClient {
    PassFeed fetch(double lat, double lon);
}
