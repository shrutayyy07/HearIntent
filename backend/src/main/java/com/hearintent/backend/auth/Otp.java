package com.hearintent.backend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("otps")
public class Otp {

    @Id
    private UUID id;
    private String email;
    private String codeHash;
    private String purpose;
    private int attempts;
    private int maxAttempts;
    private boolean isUsed;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant lastSentAt;

    public Otp() {
    }

    public static Otp create(String email, String codeHash, String purpose,
                              int maxAttempts, Instant expiresAt) {
        Otp otp = new Otp();
        Instant now = Instant.now();
        otp.email = email;
        otp.codeHash = codeHash;
        otp.purpose = purpose;
        otp.attempts = 0;
        otp.maxAttempts = maxAttempts;
        otp.isUsed = false;
        otp.expiresAt = expiresAt;
        otp.createdAt = now;
        otp.lastSentAt = now;
        return otp;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public boolean isUsed() { return isUsed; }
    public void setUsed(boolean used) { isUsed = used; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
}
