package com.hearintent.backend.websocket.dto;

public record SystemStatusPayload(
        boolean frontendOnline,
        boolean backendOnline,
        boolean pythonWorkerOnline,
        boolean websocketOnline
) {
}
