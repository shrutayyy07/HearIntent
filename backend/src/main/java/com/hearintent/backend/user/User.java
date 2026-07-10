package com.hearintent.backend.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
public class User {

    @Id
    private UUID id;
    private String email;
    private String displayName;
    private String phoneNumber;
    private String passwordHash;
    private boolean isActive;
    private boolean isVerified;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    public User() {
    }

    public User(UUID id, String email, String displayName, boolean isActive,
                boolean isVerified, Instant createdAt, Instant updatedAt, Instant lastLoginAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.isActive = isActive;
        this.isVerified = isVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }

    public static User newUser(String email) {
        Instant now = Instant.now();
        User user = new User();
        user.email = email;
        user.displayName = email.split("@")[0];
        user.isActive = true;
        user.isVerified = false;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
