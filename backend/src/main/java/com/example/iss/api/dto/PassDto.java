package com.example.iss.api.dto;

public record PassDto(
        String rise,
        String culmination,
        String set,
        long durationSec,
        String riseDirection,
        double riseAzimuthDeg,
        String setDirection,
        double setAzimuthDeg,
        double maxElevationDeg,
        boolean visible,
        boolean observable,
        String visibleStart,
        String visibleEnd,
        long visibleDurationSec,
        Double magnitude,
        Integer cloudCoverPct
) {
}
