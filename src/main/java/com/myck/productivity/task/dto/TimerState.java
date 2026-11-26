package com.myck.productivity.task.dto;

/** Situação do cronômetro de uma tarefa. */
public enum TimerState {
    /** Tarefa que nunca foi iniciada. */
    IDLE,
    /** Há uma sessão rodando agora. */
    RUNNING,
    /** Tarefa iniciada sem sessão rodando: foi pausada ou o tempo escolhido acabou. */
    PAUSED,
    /** Tarefa concluída. */
    COMPLETED
}
