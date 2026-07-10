package com.hearintent.backend.session;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("sessions")
public class Session {

    @Id
    private UUID id;
    private UUID userId;
    private String refreshTokenHash;
    private String userAgent;
    private String ipAddress;
    private boolean isRevoked;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    public Session() {
    }

    public static Session create(UUID userId, String refreshTokenHash, String userAgent,
                                  String ipAddress, Instant expiresAt) {
        Session session = new Session();
        Instant now = Instant.now();
        session.userId = userId;
        session.refreshTokenHash = refreshTokenHash;
        session.userAgent = userAgent;
        session.ipAddress = ipAddress;
        session.isRevoked = false;
        session.createdAt = now;
        session.expiresAt = expiresAt;
        session.lastUsedAt = now;
        return session;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public boolean isRevoked() { return isRevoked; }
    public void setRevoked(boolean revoked) { isRevoked = revoked; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
