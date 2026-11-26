package com.myck.productivity.task.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cálculo do tempo restante do bloco de timer. Um bloco é o tempo escolhido (15, 30, 45 ou 60 min) e pode
 * ser gasto em várias sessões, pois a pausa preserva o que sobrou. O restante é a duração escolhida menos o
 * tempo já usado pelas sessões do bloco.
 */
public final class TimerBlocks {

    private TimerBlocks() {
    }

    /**
     * Tempo restante do bloco da sessão mais recente. Vazio se não há sessões ou se a mais recente é de
     * cronômetro livre (sem limite). Nunca é negativo: bloco todo usado devolve zero.
     *
     * @param sessions sessões da tarefa, da mais antiga para a mais recente
     */
    public static Optional<Duration> remaining(List<TaskSession> sessions, Instant now) {
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        TaskSession latest = sessions.getLast();
        if (latest.getBlockId() == null) {
            return Optional.empty();
        }
        // Só as sessões do mesmo bloco gastam o mesmo tempo; blocos antigos de outras rodadas não contam
        Duration used = sessions.stream()
                .filter(session -> latest.getBlockId().equals(session.getBlockId()))
                .map(session -> session.durationAt(now))
                .reduce(Duration.ZERO, Duration::plus);
        Duration remaining = Duration.ofMinutes(latest.getPlannedMinutes()).minus(used);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }
}
