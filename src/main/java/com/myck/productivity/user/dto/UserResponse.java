package com.myck.productivity.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Dados públicos do usuário. Nunca inclui a senha nem o hash.
 *
 * @param timeZone              fuso do usuário (ex.: America/Sao_Paulo)
 * @param dailyFocusGoalMinutes meta diária de foco em minutos; ausente quando não definida
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(Long id, String name, String email, String timeZone, Integer dailyFocusGoalMinutes,
        Instant createdAt) {
}
