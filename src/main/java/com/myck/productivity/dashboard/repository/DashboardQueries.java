package com.myck.productivity.dashboard.repository;

import com.myck.productivity.dashboard.dto.CategoryStat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consultas agregadas do dashboard, relatórios e calendário em SQL nativo (via JdbcClient): somas de
 * intervalos de tempo e agrupamento por dia no fuso do usuário, que o JPQL não expressa.
 *
 * Regras comuns a todas as consultas:
 * <ul>
 *   <li>Os dias são do fuso do usuário: o dia D começa em {@code D::timestamp AT TIME ZONE :tz} e termina no
 *       início de D+1. Assim uma tarefa concluída às 22h em São Paulo conta no mesmo dia, e não no seguinte (UTC).</li>
 *   <li>O fim efetivo de uma sessão é o fim gravado; senão o limite do timer se já passou; senão "agora"
 *       (o LEAST do PostgreSQL ignora nulos). Uma sessão vencida que o banco ainda guarda como aberta
 *       conta só até o limite escolhido, nunca até "agora".</li>
 *   <li>O tempo de foco de um período é a parte de cada sessão que cai dentro dele: uma sessão das 23h30
 *       à 0h30 conta 30 minutos para cada dia.</li>
 *   <li>Cuidado: no PostgreSQL, LEAST e GREATEST ignoram nulos. Em LEFT JOIN, a interseção só pode ser
 *       calculada quando existe a linha do lado direito.</li>
 * </ul>
 */
@Repository
public class DashboardQueries {

    private static final String EFFECTIVE_END =
            "COALESCE(s.ended_at, LEAST(CAST(:now AS timestamptz), s.expires_at))";
    private static final String RANGE_START = "(CAST(:from AS timestamp) AT TIME ZONE :tz)";
    private static final String RANGE_END = "((CAST(:to AS date) + 1)::timestamp AT TIME ZONE :tz)";

    private final JdbcClient jdbc;

    public DashboardQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Segundos de foco por dia, de {@code from} a {@code to} (inclusive), no fuso informado. Devolve todos
     * os dias do intervalo, com zero nos dias sem foco. Cada dia soma a interseção das sessões do usuário
     * com o próprio dia (arredondada para baixo).
     */
    public Map<LocalDate, Long> focusSecondsByDay(Long userId, LocalDate from, LocalDate to, ZoneId zone,
            Instant now) {
        return jdbc.sql("""
                        WITH days AS (
                            SELECT d::date AS day,
                                   (d AT TIME ZONE :tz) AS day_start,
                                   ((d + interval '1 day') AT TIME ZONE :tz) AS day_end
                              FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp),
                                                   interval '1 day') AS d
                        ),
                        sessions AS (
                            SELECT s.started_at, %1$s AS effective_end
                              FROM task_sessions s
                             WHERE s.user_id = :userId
                               AND s.started_at < %3$s
                               AND %1$s > %2$s
                        )
                        SELECT days.day,
                               FLOOR(COALESCE(SUM(
                                   -- Só calcula quando há sessão: LEAST/GREATEST ignoram nulos e, num dia sem
                                   -- sessões, LEAST(NULL, day_end) - GREATEST(NULL, day_start) daria o dia inteiro
                                   CASE WHEN sessions.started_at IS NOT NULL THEN EXTRACT(EPOCH FROM (
                                       LEAST(sessions.effective_end, days.day_end)
                                       - GREATEST(sessions.started_at, days.day_start)))
                                   END), 0)) AS focus_seconds
                          FROM days
                          LEFT JOIN sessions
                            ON sessions.started_at < days.day_end AND sessions.effective_end > days.day_start
                         GROUP BY days.day
                         ORDER BY days.day
                        """.formatted(EFFECTIVE_END, RANGE_START, RANGE_END))
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .param("tz", zone.getId())
                .param("now", utc(now))
                .query((rs, rowNum) -> Map.entry(rs.getObject("day", LocalDate.class), rs.getLong("focus_seconds")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Tarefas concluídas por dia (pelo momento da conclusão, no fuso informado), de {@code from} a {@code to}.
     * Só aparecem os dias com ao menos uma conclusão.
     */
    public Map<LocalDate, Long> completedTasksByDay(Long userId, LocalDate from, LocalDate to, ZoneId zone) {
        return jdbc.sql("""
                        SELECT (t.completed_at AT TIME ZONE :tz)::date AS day, COUNT(*) AS completed
                          FROM tasks t
                         WHERE t.user_id = :userId
                           AND t.status = 'COMPLETED'
                           AND t.completed_at >= %s
                           AND t.completed_at < %s
                         GROUP BY 1
                        """.formatted(RANGE_START, RANGE_END))
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .param("tz", zone.getId())
                .query((rs, rowNum) -> Map.entry(rs.getObject("day", LocalDate.class), rs.getLong("completed")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Desempenho por categoria no período: tarefas concluídas no período e tempo de foco dentro do período.
     * Só entram categorias com alguma atividade. Ordem: mais tarefas concluídas primeiro; empate decidido
     * pelo tempo de foco e depois pelo nome.
     */
    public List<CategoryStat> categoryStats(Long userId, LocalDate from, LocalDate to, ZoneId zone, Instant now) {
        return jdbc.sql("""
                        WITH completed AS (
                            SELECT t.category_id, COUNT(*) AS tasks_completed
                              FROM tasks t
                             WHERE t.user_id = :userId
                               AND t.status = 'COMPLETED'
                               AND t.completed_at >= %2$s
                               AND t.completed_at < %3$s
                             GROUP BY t.category_id
                        ),
                        focus AS (
                            SELECT t.category_id,
                                   SUM(EXTRACT(EPOCH FROM (LEAST(%1$s, %3$s) - GREATEST(s.started_at, %2$s)))) AS seconds
                              FROM task_sessions s
                              JOIN tasks t ON t.id = s.task_id
                             WHERE s.user_id = :userId
                               AND s.started_at < %3$s
                               AND %1$s > %2$s
                             GROUP BY t.category_id
                        )
                        SELECT c.id, c.name, c.color,
                               COALESCE(completed.tasks_completed, 0) AS tasks_completed,
                               FLOOR(COALESCE(focus.seconds, 0)) AS focus_seconds
                          FROM categories c
                          LEFT JOIN completed ON completed.category_id = c.id
                          LEFT JOIN focus ON focus.category_id = c.id
                         WHERE c.user_id = :userId
                           AND (completed.tasks_completed > 0 OR focus.seconds > 0)
                         ORDER BY tasks_completed DESC, focus_seconds DESC, c.name ASC
                        """.formatted(EFFECTIVE_END, RANGE_START, RANGE_END))
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .param("tz", zone.getId())
                .param("now", utc(now))
                .query((rs, rowNum) -> new CategoryStat(rs.getLong("id"), rs.getString("name"),
                        rs.getString("color"), rs.getLong("tasks_completed"), rs.getLong("focus_seconds")))
                .list();
    }

    /**
     * Tarefas planejadas para o dia (pela data planejada, que já é uma data sem fuso) e quantas delas estão
     * concluídas, não importa quando foram concluídas.
     */
    public PlannedCount plannedTasks(Long userId, LocalDate date) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS planned,
                               COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS planned_completed
                          FROM tasks t
                         WHERE t.user_id = :userId AND t.planned_date = :date
                        """)
                .param("userId", userId)
                .param("date", date)
                .query((rs, rowNum) -> new PlannedCount(rs.getLong("planned"), rs.getLong("planned_completed")))
                .single();
    }

    /** Planejadas para um dia e quantas delas estão concluídas. */
    public record PlannedCount(long planned, long completed) {
    }

    // O driver JDBC do PostgreSQL não aceita Instant como parâmetro; OffsetDateTime em UTC vira timestamptz
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
