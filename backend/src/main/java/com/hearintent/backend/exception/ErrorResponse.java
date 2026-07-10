package com.hearintent.backend.exception;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(String errorCode, String message, int status, String path) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), path);
    }
}
