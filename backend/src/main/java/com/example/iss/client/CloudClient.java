package com.example.iss.client;

import com.example.iss.domain.CloudForecast;

/**
 * Adapter port for hourly cloud-cover forecast, observer-location dependent
 * (PROMPT.md section 4).
 */
public interface CloudClient {
    CloudForecast fetch(double lat, double lon);
}
