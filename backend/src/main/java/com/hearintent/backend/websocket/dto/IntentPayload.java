package com.hearintent.backend.websocket.dto;

import java.util.Map;

public record IntentPayload(
        String intent,
        double confidence,
        Map<String, Object> entities,
        String ruleAction
) {
}
