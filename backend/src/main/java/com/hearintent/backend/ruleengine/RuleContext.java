package com.hearintent.backend.ruleengine;

import java.util.Map;

public record RuleContext(
        String intent,
        double confidence,
        Map<String, Object> entities,
        String rawText
) {
    public boolean hasEntity(String key) {
        return entities != null && entities.containsKey(key);
    }

    public Object entity(String key) {
        return entities == null ? null : entities.get(key);
    }
}
