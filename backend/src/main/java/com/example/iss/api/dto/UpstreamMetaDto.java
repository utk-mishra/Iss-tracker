package com.example.iss.api.dto;

public record UpstreamMetaDto(String source, boolean degraded, long ageMs) {
}
