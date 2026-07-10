package com.hearintent.backend.intent;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("intents")
public class IntentRecord {

    @Id
    private UUID id;
    private UUID sessionId;
    private UUID transcriptId;
    private String intent;
    private double confidence;
    private String entitiesJson;
    private String rawText;
    private String ruleAction;
    private Instant createdAt;

    public IntentRecord() {
    }

    public static IntentRecord create(UUID sessionId, UUID transcriptId, String intent, double confidence,
                                       String entitiesJson, String rawText, String ruleAction) {
        IntentRecord r = new IntentRecord();
        r.sessionId = sessionId;
        r.transcriptId = transcriptId;
        r.intent = intent;
        r.confidence = confidence;
        r.entitiesJson = entitiesJson == null ? "{}" : entitiesJson;
        r.rawText = rawText;
        r.ruleAction = ruleAction;
        r.createdAt = Instant.now();
        return r;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getTranscriptId() { return transcriptId; }
    public void setTranscriptId(UUID transcriptId) { this.transcriptId = transcriptId; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getEntitiesJson() { return entitiesJson; }
    public void setEntitiesJson(String entitiesJson) { this.entitiesJson = entitiesJson; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getRuleAction() { return ruleAction; }
    public void setRuleAction(String ruleAction) { this.ruleAction = ruleAction; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
