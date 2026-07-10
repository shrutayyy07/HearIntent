package com.hearintent.backend.websocket.dto;

public record WsOutboundMessage(
        String type,
        Object payload,
        long timestampMs
) {
    public static WsOutboundMessage of(String type, Object payload) {
        return new WsOutboundMessage(type, payload, System.currentTimeMillis());
    }
}
