package com.example.iss.api.dto;

import java.util.List;

public record PassesResponseDto(
        List<PassDto> passes,
        MetaDto meta,
        LinksDto _links
) {
}
