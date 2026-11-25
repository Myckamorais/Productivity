package com.myck.productivity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo do POST /auth/login. */
public record LoginRequest(
        @NotBlank(message = "O e-mail é obrigatório.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        String password) {
}
