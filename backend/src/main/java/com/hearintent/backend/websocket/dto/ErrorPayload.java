package com.hearintent.backend.websocket.dto;

public record ErrorPayload(
        String code,
        String message
) {
}
