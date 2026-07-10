package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneOtpVerifyDto(
    @NotBlank String phoneNumber,
    @NotBlank @Size(min = 6, max = 6) String code
) {}
