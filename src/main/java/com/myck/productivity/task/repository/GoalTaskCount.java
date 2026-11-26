package com.myck.productivity.task.repository;

/** Contagem de tarefas de uma meta: quantas existem e quantas estão concluídas. */
public record GoalTaskCount(Long goalId, Long total, Long completed) {
}
