package com.example.iss.api.dto;

import java.util.List;

public record OverviewResponseDto(
        PositionDto position,
        List<PassDto> passes,
        PassDto nextVisiblePass,
        String message,
        List<String> alerts,
        MetaDto meta,
        LinksDto _links
) {
}
