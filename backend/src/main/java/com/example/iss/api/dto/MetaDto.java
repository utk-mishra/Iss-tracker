package com.example.iss.api.dto;

public record MetaDto(
        UpstreamMetaDto position,
        PassesMetaDto passes,
        UpstreamMetaDto cloud
) {
}
