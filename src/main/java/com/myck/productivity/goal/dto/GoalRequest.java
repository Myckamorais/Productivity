package com.myck.productivity.goal.dto;

import com.myck.productivity.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo do POST e do PUT de /goals.
 *
 * @param status opcional: na criação, se omitido, a meta começa IN_PROGRESS; na edição, se omitido, o status atual é mantido
 */
public record GoalRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String title,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
        String description,

        LocalDate deadline,

        GoalStatus status) {
}
