package com.hearintent.backend.auth.dto;

public record MessageResponseDto(
        String message,
        boolean success
) {
    public static MessageResponseDto ok(String message) {
        return new MessageResponseDto(message, true);
    }

    public static MessageResponseDto fail(String message) {
        return new MessageResponseDto(message, false);
    }
}
