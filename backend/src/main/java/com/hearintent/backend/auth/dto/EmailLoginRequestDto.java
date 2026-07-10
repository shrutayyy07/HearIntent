package com.hearintent.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailLoginRequestDto(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
