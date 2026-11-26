package com.myck.productivity.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myck.productivity.task.entity.TaskStatus;

import java.time.Instant;
import java.util.List;

/**
 * Situação do cronômetro de uma tarefa.
 *
 * @param totalSeconds     tempo investido na tarefa: soma de todas as sessões, sem passar do limite escolhido em cada uma
 * @param remainingSeconds tempo que falta do timer escolhido, tanto rodando quanto pausado (a pausa preserva o
 *                         restante). Zero quando o tempo escolhido acabou. Nulo no cronômetro livre, em tarefa
 *                         nunca iniciada e em tarefa concluída
 * @param activeSession    a sessão rodando agora; nulo quando não há
 * @param sessions         todas as sessões, da mais antiga para a mais recente
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerResponse(
        Long taskId,
        TaskStatus taskStatus,
        TimerState state,
        long totalSeconds,
        Long remainingSeconds,
        ActiveSession activeSession,
        List<SessionItem> sessions) {

    /**
     * A sessão rodando agora.
     *
     * @param plannedMinutes   duração escolhida; nulo no cronômetro livre
     * @param expiresAt        quando o tempo escolhido acaba; nulo no cronômetro livre
     * @param remainingSeconds tempo que falta; nulo no cronômetro livre
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActiveSession(Long id, Instant startedAt, Integer plannedMinutes, Instant expiresAt,
            long elapsedSeconds, Long remainingSeconds) {
    }

    /**
     * Uma sessão do histórico.
     *
     * @param endedAt fim efetivo da sessão (o limite, se o tempo escolhido acabou); nulo se ainda está rodando
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionItem(Long id, Instant startedAt, Instant endedAt, Integer plannedMinutes,
            long durationSeconds) {
    }
}
