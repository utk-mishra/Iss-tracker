package com.example.iss.api.dto;

/**
 * Single error response shape used for 400, 429 and 500 (Step 6 decision 6).
 * Never carries a stack trace or internal exception details.
 */
public record ErrorDto(String error, int status) {
}
