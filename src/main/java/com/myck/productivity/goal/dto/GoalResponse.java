package com.myck.productivity.goal.dto;

import com.myck.productivity.goal.entity.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;

public record GoalResponse(Long id, String title, String description, LocalDate deadline, GoalStatus status,
        Instant createdAt) {
}
