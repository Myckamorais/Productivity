package com.myck.productivity.goal.mapper;

import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.Goal;
import org.springframework.stereotype.Component;

/** Converte a entidade em DTO: a entidade nunca sai pela API. */
@Component
public class GoalMapper {

    public GoalResponse toResponse(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getDeadline(),
                goal.getStatus(), goal.getCreatedAt());
    }
}
