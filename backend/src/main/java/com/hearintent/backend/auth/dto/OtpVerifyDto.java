package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyDto(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{4,8}", message = "OTP must be numeric") String code
) {
}
