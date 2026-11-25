package com.myck.productivity.user.dto;

import java.time.Instant;

/** Dados públicos do usuário. Nunca inclui a senha nem o hash. */
public record UserResponse(Long id, String name, String email, Instant createdAt) {
}
