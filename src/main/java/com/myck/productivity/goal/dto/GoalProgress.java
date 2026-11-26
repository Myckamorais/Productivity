package com.myck.productivity.goal.dto;

/**
 * Progresso de uma meta: tarefas concluídas em relação ao total de tarefas dela.
 */
public record GoalProgress(long totalTasks, long completedTasks) {

    /** Meta sem nenhuma tarefa. */
    public static final GoalProgress EMPTY = new GoalProgress(0, 0);

    /** Percentual de 0 a 100, arredondado. Sem tarefas o progresso é 0 (e não uma divisão por zero). */
    public int percent() {
        if (totalTasks == 0) {
            return 0;
        }
        return (int) Math.round(completedTasks * 100.0 / totalTasks);
    }
}
