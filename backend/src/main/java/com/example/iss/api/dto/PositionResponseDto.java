package com.example.iss.api.dto;

public record PositionResponseDto(
        PositionDto position,
        UpstreamMetaDto meta,
        LinksDto _links
) {
}
