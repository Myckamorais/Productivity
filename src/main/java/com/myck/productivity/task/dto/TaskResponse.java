package com.myck.productivity.task.dto;

import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param totalSeconds tempo investido na tarefa pelo cronômetro (0 se nunca foi cronometrada)
 * @param timerState   situação do cronômetro (IDLE, RUNNING, PAUSED ou COMPLETED); detalhes em GET /tasks/{id}/timer
 */
public record TaskResponse(Long id, String title, String description, Long categoryId, Long goalId,
        TaskPriority priority, TaskStatus status, LocalDate plannedDate, Instant completedAt, Instant createdAt,
        long totalSeconds, TimerState timerState) {
}
