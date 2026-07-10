package com.hearintent.backend.ruleengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class IntentRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(IntentRuleEngine.class);

    private final List<IntentRule> rules = new CopyOnWriteArrayList<>();

    public IntentRuleEngine() {
        registerDefaultRules();
    }

    public void registerRule(IntentRule rule) {
        rules.add(rule);
    }

    private boolean matchesIntent(RuleContext ctx, String... keywords) {
        String intentText = ctx.intent();
        if (intentText == null || "none".equals(intentText)) {
            return false;
        }
        String lowerIntent = intentText.toLowerCase();
        for (String keyword : keywords) {
            if (!lowerIntent.contains(keyword.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private void registerDefaultRules() {
        registerRule(new IntentRule(
                "ScheduleMeetingWithTimeRule",
                ctx -> (matchesIntent(ctx, "schedule", "meeting") || matchesIntent(ctx, "book", "meeting"))
                        && ctx.hasEntity("time"),
                "CREATE_CALENDAR_EVENT",
                100
        ));

        registerRule(new IntentRule(
                "SetReminderRule",
                ctx -> matchesIntent(ctx, "remind") || matchesIntent(ctx, "set", "reminder"),
                "CREATE_REMINDER",
                90
        ));

        registerRule(new IntentRule(
                "SendMessageRule",
                ctx -> matchesIntent(ctx, "send", "message") || matchesIntent(ctx, "text"),
                "DISPATCH_MESSAGE",
                85
        ));

        registerRule(new IntentRule(
                "MakeCallRule",
                ctx -> matchesIntent(ctx, "call") || matchesIntent(ctx, "phone"),
                "INITIATE_CALL",
                85
        ));

        registerRule(new IntentRule(
                "CancelEventRule",
                ctx -> matchesIntent(ctx, "cancel", "event") || matchesIntent(ctx, "cancel", "meeting"),
                "CANCEL_CALENDAR_EVENT",
                80
        ));

        registerRule(new IntentRule(
                "HighValueTransactionAlertRule",
                ctx -> ctx.hasEntity("amount") && parseAmount(ctx.entity("amount")) >= 10000,
                "FIRE_HIGH_PRIORITY_ALERT",
                200
        ));

        registerRule(new IntentRule(
                "ComplaintEscalationRule",
                ctx -> matchesIntent(ctx, "complain") || matchesIntent(ctx, "angry") || matchesIntent(ctx, "issue"),
                "ESCALATE_TO_SUPPORT",
                95
        ));

        registerRule(new IntentRule(
                "InstructionRule",
                ctx -> !"none".equals(ctx.intent()) && !ctx.intent().isEmpty(),
                "LOG_INSTRUCTION",
                10
        ));
    }

    private double parseAmount(Object raw) {
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public RuleEvaluationResult evaluate(RuleContext context) {
        List<IntentRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(IntentRule::priority).reversed());

        for (IntentRule rule : sorted) {
            try {
                if (rule.condition().test(context)) {
                    log.debug("Rule matched: {} -> {}", rule.name(), rule.actionCode());
                    return new RuleEvaluationResult(
                            true,
                            rule.name(),
                            rule.actionCode(),
                            "Matched rule '" + rule.name() + "' for intent '" + context.intent() + "'"
                    );
                }
            } catch (Exception e) {
                log.warn("Rule '{}' threw an exception during evaluation: {}", rule.name(), e.getMessage());
            }
        }
        return RuleEvaluationResult.noMatch();
    }
}
