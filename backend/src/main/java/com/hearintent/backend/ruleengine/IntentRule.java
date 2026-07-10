package com.hearintent.backend.ruleengine;

import java.util.function.Predicate;

public record IntentRule(
        String name,
        Predicate<RuleContext> condition,
        String actionCode,
        int priority
) {
}
