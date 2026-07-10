package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneOtpRequestDto(
    @NotBlank String phoneNumber
) {}
