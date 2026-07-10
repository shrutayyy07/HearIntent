package com.hearintent.backend.ruleengine;

public record RuleEvaluationResult(
        boolean matched,
        String ruleName,
        String actionCode,
        String description
) {
    public static RuleEvaluationResult noMatch() {
        return new RuleEvaluationResult(false, null, "NO_ACTION", "No matching rule found.");
    }
}
