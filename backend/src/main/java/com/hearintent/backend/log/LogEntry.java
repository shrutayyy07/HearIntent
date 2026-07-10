package com.hearintent.backend.log;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("logs")
public class LogEntry {

    @Id
    private UUID id;
    private UUID userId;
    private UUID sessionId;
    private String level;
    private String category;
    private String message;
    private String metadataJson;
    private Instant createdAt;

    public LogEntry() {
    }

    public static LogEntry create(UUID userId, UUID sessionId, String level, String category,
                                   String message, String metadataJson) {
        LogEntry entry = new LogEntry();
        entry.userId = userId;
        entry.sessionId = sessionId;
        entry.level = level;
        entry.category = category;
        entry.message = message;
        entry.metadataJson = metadataJson == null ? "{}" : metadataJson;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
