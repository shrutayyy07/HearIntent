package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordVerifyDto(
        @NotBlank(message = "Email is required.")
        @Email(message = "Please provide a valid email address.")
        String email,

        @NotBlank(message = "Verification code is required.")
        String otp,

        @NotBlank(message = "New password is required.")
        @Size(min = 6, message = "Password must be at least 6 characters.")
        String newPassword
) {}
