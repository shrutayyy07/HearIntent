package com.hearintent.backend.transcript;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("transcripts")
public class Transcript {

    @Id
    private UUID id;
    private UUID sessionId;
    private String text;
    private double confidence;
    private Double startTimeSec;
    private Double endTimeSec;
    private String language;
    private boolean isFinal;
    private Instant createdAt;

    public Transcript() {
    }

    public static Transcript create(UUID sessionId, String text, double confidence,
                                     Double startTimeSec, Double endTimeSec, String language, boolean isFinal) {
        Transcript t = new Transcript();
        t.sessionId = sessionId;
        t.text = text;
        t.confidence = confidence;
        t.startTimeSec = startTimeSec;
        t.endTimeSec = endTimeSec;
        t.language = language;
        t.isFinal = isFinal;
        t.createdAt = Instant.now();
        return t;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Double getStartTimeSec() { return startTimeSec; }
    public void setStartTimeSec(Double startTimeSec) { this.startTimeSec = startTimeSec; }

    public Double getEndTimeSec() { return endTimeSec; }
    public void setEndTimeSec(Double endTimeSec) { this.endTimeSec = endTimeSec; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isFinal() { return isFinal; }
    public void setFinal(boolean aFinal) { isFinal = aFinal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
