package com.hearintent.backend.session;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("speech_sessions")
public class SpeechSession {

    @Id
    private UUID id;
    private UUID userId;
    private String sourceType;
    private String originalFilename;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private Double durationSec;

    public SpeechSession() {
    }

    public static SpeechSession startLive(UUID userId) {
        SpeechSession s = new SpeechSession();
        s.userId = userId;
        s.sourceType = "LIVE";
        s.status = "ACTIVE";
        s.startedAt = Instant.now();
        return s;
    }

    public static SpeechSession startFileUpload(UUID userId, String filename) {
        SpeechSession s = new SpeechSession();
        s.userId = userId;
        s.sourceType = "FILE_UPLOAD";
        s.originalFilename = filename;
        s.status = "ACTIVE";
        s.startedAt = Instant.now();
        return s;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Double getDurationSec() { return durationSec; }
    public void setDurationSec(Double durationSec) { this.durationSec = durationSec; }
}
