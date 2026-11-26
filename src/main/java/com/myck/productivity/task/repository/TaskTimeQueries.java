package com.myck.productivity.task.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consultas de tempo das tarefas em SQL nativo (via JdbcClient): agregações com funções do PostgreSQL
 * (COALESCE, LEAST, EXTRACT) que o JPQL não expressa bem.
 *
 * O "fim efetivo" de uma sessão é COALESCE(ended_at, LEAST(:now, expires_at)): o fim gravado; senão o
 * limite do timer se já passou; senão "agora" (o LEAST do PostgreSQL ignora nulos, então no cronômetro
 * livre vale :now). É a mesma regra de TaskSession.effectiveEnd, para a lista e o /timer baterem.
 */
@Repository
public class TaskTimeQueries {

    private final JdbcClient jdbc;

    public TaskTimeQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Para cada tarefa informada: total de segundos investidos (somando todas as sessões até o fim efetivo,
     * nunca negativo) e se há sessão rodando agora (aberta e, se tem limite, ainda dentro dele). Uma única
     * consulta agrupada para a página inteira. Tarefas sem sessão não aparecem no mapa.
     */
    public Map<Long, TaskTimeSummary> summaries(Collection<Long> taskIds, Instant now) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT task_id,
                               FLOOR(SUM(GREATEST(0, EXTRACT(EPOCH FROM (
                                   COALESCE(ended_at, LEAST(CAST(:now AS timestamptz), expires_at)) - started_at
                               ))))) AS total_seconds,
                               BOOL_OR(ended_at IS NULL
                                       AND (expires_at IS NULL OR expires_at > CAST(:now AS timestamptz))) AS running
                          FROM task_sessions
                         WHERE task_id IN (:taskIds)
                         GROUP BY task_id
                        """)
                .param("now", utc(now))
                .param("taskIds", taskIds)
                .query((rs, rowNum) -> Map.entry(rs.getLong("task_id"),
                        new TaskTimeSummary(rs.getLong("total_seconds"), rs.getBoolean("running"))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Tempo de uma única tarefa (sem sessões, devolve {@link TaskTimeSummary#NONE}). */
    public TaskTimeSummary summary(Long taskId, Instant now) {
        return summaries(List.of(taskId), now).getOrDefault(taskId, TaskTimeSummary.NONE);
    }

    // O driver JDBC do PostgreSQL não aceita Instant como parâmetro; OffsetDateTime em UTC vira timestamptz
    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
