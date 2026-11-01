package com.example.iss.api;

/**
 * Thrown for invalid/missing/non-finite lat, lon, or offline query parameters
 * (PROMPT.md section 6; Step 6 decision/addition d). Handled by
 * {@link ApiExceptionHandler} as a 400 with a clean {@link com.example.iss.api.dto.ErrorDto}.
 */
public class InvalidLocationException extends RuntimeException {

    public InvalidLocationException(String message) {
        super(message);
    }
}
