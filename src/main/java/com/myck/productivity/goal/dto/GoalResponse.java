package com.myck.productivity.goal.dto;

import com.myck.productivity.goal.entity.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param progressPercent tarefas concluídas / total de tarefas da meta, de 0 a 100 (0 quando não há tarefas)
 */
public record GoalResponse(Long id, String title, String description, LocalDate deadline, GoalStatus status,
        long totalTasks, long completedTasks, int progressPercent, Instant createdAt) {
}
