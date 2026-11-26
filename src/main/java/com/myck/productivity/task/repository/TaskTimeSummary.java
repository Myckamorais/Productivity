package com.myck.productivity.task.repository;

/**
 * Tempo investido numa tarefa e se o cronômetro dela está rodando agora.
 *
 * @param totalSeconds soma das sessões, cada uma parando no limite escolhido
 */
public record TaskTimeSummary(long totalSeconds, boolean running) {

    /** Tarefa sem nenhuma sessão. */
    public static final TaskTimeSummary NONE = new TaskTimeSummary(0, false);
}
