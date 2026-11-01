package com.example.iss.api.dto;

public record PassesMetaDto(UpstreamMetaDto meta, double tleAgeHours, boolean tleStale) {
}
