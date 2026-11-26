package com.myck.productivity.task.dto;

import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(Long id, String title, String description, Long categoryId, Long goalId,
        TaskPriority priority, TaskStatus status, LocalDate plannedDate, Instant completedAt, Instant createdAt) {
}
