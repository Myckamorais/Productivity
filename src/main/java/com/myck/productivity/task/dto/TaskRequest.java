package com.myck.productivity.task.dto;

import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo do POST e do PUT de /tasks.
 *
 * @param goalId      opcional; no PUT, omitir desvincula a tarefa da meta
 * @param priority    opcional: na criação vira MEDIUM; na edição, se omitida, mantém a atual
 * @param status      opcional: na criação vira PENDING; na edição, se omitido, mantém o atual
 * @param plannedDate opcional; no PUT, omitir limpa a data
 */
public record TaskRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String title,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
        String description,

        @NotNull(message = "A categoria é obrigatória.")
        Long categoryId,

        Long goalId,

        TaskPriority priority,

        TaskStatus status,

        LocalDate plannedDate) {
}
