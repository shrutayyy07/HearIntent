package com.hearintent.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hearintent")
public record HearIntentProperties(
        Jwt jwt,
        Otp otp,
        RateLimit rateLimit,
        Grpc grpc,
        Logging logging,
        Cors cors
) {
    public record Jwt(
            String secret,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays,
            String issuer
    ) {}

    public record Otp(
            int length,
            long ttlMinutes,
            long resendCooldownSeconds,
            int maxAttempts,
            String mailFrom,
            boolean mockMode
    ) {}

    public record RateLimit(
            int otpRequestsPerHour,
            int apiRequestsPerMinute
    ) {}

    public record Grpc(
            String aiWorkerHost,
            int aiWorkerPort,
            int deadlineSeconds
    ) {}

    public record Logging(
            String flatLogDir
    ) {}

    public record Cors(
            List<String> allowedOrigins
    ) {}
}
