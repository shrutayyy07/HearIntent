package com.hearintent.backend.websocket.dto;

public record WsControlMessage(
        String type,
        Integer sampleRate
) {
}
