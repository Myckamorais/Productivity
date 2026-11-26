package com.myck.productivity.dashboard.dto;

/**
 * Desempenho de uma categoria no período.
 *
 * @param tasksCompleted tarefas da categoria concluídas no período (critério do ranking)
 * @param focusSeconds   tempo de foco cronometrado em tarefas da categoria dentro do período (desempate)
 */
public record CategoryStat(Long categoryId, String name, String color, long tasksCompleted, long focusSeconds) {
}
