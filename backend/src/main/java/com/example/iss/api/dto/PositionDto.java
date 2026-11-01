package com.example.iss.api.dto;

public record PositionDto(
        double latitude,
        double longitude,
        double altitudeKm,
        double velocityKmh,
        String observedAt,
        String note
) {
}
