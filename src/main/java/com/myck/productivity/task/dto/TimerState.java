package com.myck.productivity.task.dto;

import com.myck.productivity.task.entity.TaskStatus;

/** Situação do cronômetro de uma tarefa. */
public enum TimerState {
    /** Tarefa que nunca foi iniciada. */
    IDLE,
    /** Há uma sessão rodando agora. */
    RUNNING,
    /** Tarefa iniciada sem sessão rodando: foi pausada ou o tempo escolhido acabou. */
    PAUSED,
    /** Tarefa concluída. */
    COMPLETED;

    /**
     * Deduz o estado a partir do status da tarefa e de haver sessão rodando. Concluída vence; senão, sessão
     * rodando = RUNNING; tarefa em andamento sem sessão rodando = PAUSED; o resto = IDLE.
     * Regra única, usada tanto pelo /timer quanto pela listagem de tarefas.
     */
    public static TimerState of(TaskStatus status, boolean running) {
        if (status == TaskStatus.COMPLETED) {
            return COMPLETED;
        }
        if (running) {
            return RUNNING;
        }
        return status == TaskStatus.IN_PROGRESS ? PAUSED : IDLE;
    }
}
