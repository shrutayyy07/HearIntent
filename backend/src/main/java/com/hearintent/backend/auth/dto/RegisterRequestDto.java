package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank String phoneNumber,
    @NotBlank @Size(min = 6) String password
) {}
