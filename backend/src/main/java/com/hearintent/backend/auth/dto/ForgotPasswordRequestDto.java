package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDto(
        @NotBlank(message = "Email is required.")
        @Email(message = "Please provide a valid email address.")
        String email
) {}
