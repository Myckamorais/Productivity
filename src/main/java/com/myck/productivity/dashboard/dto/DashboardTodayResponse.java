package com.myck.productivity.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myck.productivity.goal.dto.GoalResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Resumo do dia de hoje, no fuso do usuário.
 *
 * @param tasksCompleted        tarefas concluídas hoje (qualquer tarefa, planejada para hoje ou não)
 * @param plannedTasks          tarefas com data planejada para hoje
 * @param plannedTasksCompleted quantas das planejadas para hoje estão concluídas (não importa quando)
 * @param completionPercent     rendimento por tarefas: planejadas concluídas ÷ planejadas, de 0 a 100;
 *                              ausente quando não há nada planejado para hoje
 * @param focusSeconds          tempo de foco de hoje (só a parte das sessões que cai dentro de hoje)
 * @param focusGoalMinutes      meta diária de foco do usuário; ausente se ele não definiu
 * @param focusPercent          rendimento por foco: foco de hoje ÷ meta diária; pode passar de 100;
 *                              ausente sem meta definida
 * @param goalsInProgress       metas em andamento, com o progresso de cada uma
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardTodayResponse(
        LocalDate date,
        String timeZone,
        long tasksCompleted,
        long plannedTasks,
        long plannedTasksCompleted,
        Integer completionPercent,
        long focusSeconds,
        Integer focusGoalMinutes,
        Integer focusPercent,
        List<GoalResponse> goalsInProgress) {
}
