package com.example.iss.api.dto;

/** HATEOAS-style links present on every successful response (PROMPT.md section 6). */
public record LinksDto(String self, String position, String passes, String health, String docs) {
}
