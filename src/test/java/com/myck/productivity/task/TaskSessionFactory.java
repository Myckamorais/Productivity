package com.myck.productivity.task;

import com.myck.productivity.task.entity.TaskSession;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Fábrica de sessões do cronômetro para os testes unitários. */
public final class TaskSessionFactory {

    private TaskSessionFactory() {
    }

    /** Sessão aberta que inicia um bloco novo (rodando, ou com o tempo escolhido já vencido, conforme o relógio do teste). */
    public static TaskSession aberta(Long userId, Long taskId, Instant inicio, Integer minutosEscolhidos) {
        return new TaskSession(userId, taskId, inicio, minutosEscolhidos);
    }

    /** Sessão de cronômetro livre já fechada em {@code fim}. */
    public static TaskSession fechada(Long userId, Long taskId, Instant inicio, Instant fim) {
        TaskSession sessao = new TaskSession(userId, taskId, inicio, null);
        sessao.stop(fim);
        return sessao;
    }

    /**
     * Sessão de timer já fechada em {@code fim}, pertencente ao bloco informado (use o mesmo {@code bloco} em
     * várias sessões para simular pausas e retomadas do mesmo timer).
     */
    public static TaskSession fechadaDeBloco(Long userId, Long taskId, Instant inicio, Instant fim,
            int minutosEscolhidos, UUID bloco) {
        TaskSession sessao = new TaskSession(userId, taskId, inicio, minutosEscolhidos, bloco,
                Duration.ofMinutes(minutosEscolhidos));
        sessao.stop(fim);
        return sessao;
    }
}
