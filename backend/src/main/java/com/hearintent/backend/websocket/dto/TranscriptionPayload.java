package com.hearintent.backend.websocket.dto;

public record TranscriptionPayload(
        String text,
        String translatedText,
        double confidence,
        String role,
        String timestamp
) {
}
