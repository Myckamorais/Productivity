package com.myck.productivity.goal;

import com.myck.productivity.goal.entity.Goal;
import com.myck.productivity.goal.entity.GoalStatus;

import java.time.LocalDate;

/** Fábrica de metas para os testes unitários. */
public final class GoalFactory {

    private GoalFactory() {
    }

    /** Meta "Aprender Java" em andamento, do usuário e com o id informados. */
    public static Goal aprenderJava(Long id, Long userId) {
        Goal goal = new Goal(userId, "Aprender Java", "Concluir o curso", LocalDate.of(2026, 12, 31),
                GoalStatus.IN_PROGRESS);
        goal.setId(id);
        return goal;
    }
}
