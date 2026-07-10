package com.hearintent.backend.auth.dto;

import java.util.UUID;

public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        String email,
        String displayName
) {
}
