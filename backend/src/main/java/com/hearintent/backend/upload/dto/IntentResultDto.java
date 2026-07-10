package com.hearintent.backend.upload.dto;

import java.util.Map;

public record IntentResultDto(
        String intent,
        double confidence,
        Map<String, Object> entities,
        String ruleAction
) {
}
