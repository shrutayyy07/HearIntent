package com.hearintent.backend.ruleengine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentRuleEngineTest {

    private final IntentRuleEngine engine = new IntentRuleEngine();

    @Test
    void matchesScheduleMeetingRuleWhenTimeEntityPresent() {
        RuleContext ctx = new RuleContext(
                "schedule_meeting", 0.97, Map.of("time", "3 PM", "date", "tomorrow"), "schedule a meeting tomorrow at 3 pm"
        );
        RuleEvaluationResult result = engine.evaluate(ctx);
        assertTrue(result.matched());
        assertEquals("CREATE_CALENDAR_EVENT", result.actionCode());
    }

    @Test
    void firesHighPriorityAlertForLargeAmount() {
        RuleContext ctx = new RuleContext(
                "send_message", 0.6, Map.of("amount", "50000"), "send 50000 rupees to my account"
        );
        RuleEvaluationResult result = engine.evaluate(ctx);
        assertTrue(result.matched());
        assertEquals("FIRE_HIGH_PRIORITY_ALERT", result.actionCode());
    }

    @Test
    void fallsBackToLowConfidenceRuleWhenNothingElseMatches() {
        RuleContext ctx = new RuleContext("small_talk", 0.1, Map.of(), "uh okay sure");
        RuleEvaluationResult result = engine.evaluate(ctx);
        assertTrue(result.matched());
        assertEquals("LOG_ONLY_LOW_CONFIDENCE", result.actionCode());
    }

    @Test
    void noMatchReturnsNoActionForMidConfidenceUnrecognizedIntent() {
        RuleContext ctx = new RuleContext("greeting", 0.4, Map.of(), "hello there");
        RuleEvaluationResult result = engine.evaluate(ctx);
        assertEquals("NO_ACTION", result.actionCode());
    }
}
