package com.myck.productivity.task.mapper;

import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.Task;
import org.springframework.stereotype.Component;

/** Converte a entidade em DTO: a entidade nunca sai pela API. */
@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getCategoryId(),
                task.getGoalId(), task.getPriority(), task.getStatus(), task.getPlannedDate(),
                task.getCompletedAt(), task.getCreatedAt());
    }
}
