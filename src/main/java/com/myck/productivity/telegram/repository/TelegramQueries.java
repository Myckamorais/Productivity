package com.myck.productivity.telegram.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Consultas do aviso de fim do timer em SQL nativo (junta sessões, tarefas e vínculos do Telegram). */
@Repository
public class TelegramQueries {

    private final JdbcClient jdbc;

    public TelegramQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Timers cujo tempo escolhido acabou entre {@code since} e {@code now}, ainda não avisados, de usuários com
     * Telegram vinculado. Entram tanto as sessões ainda abertas no banco quanto as já fechadas exatamente no
     * limite (fechadas pela aplicação depois de vencer); as pausadas antes do fim ficam de fora, porque nesse
     * caso o tempo não "acabou". O limite {@code since} evita uma enxurrada de avisos antigos quando a
     * aplicação passa um tempo desligada.
     */
    public List<ExpiredTimer> expiredTimersToNotify(Instant now, Instant since) {
        return jdbc.sql("""
                        SELECT s.id AS session_id, l.chat_id, t.title, s.planned_minutes
                          FROM task_sessions s
                          JOIN telegram_links l ON l.user_id = s.user_id
                          JOIN tasks t ON t.id = s.task_id
                         WHERE s.expires_at <= :now
                           AND s.expires_at > :since
                           AND s.expiry_notified_at IS NULL
                           AND (s.ended_at IS NULL OR s.ended_at >= s.expires_at)
                         ORDER BY s.expires_at
                        """)
                .param("now", utc(now))
                .param("since", utc(since))
                .query((rs, rowNum) -> new ExpiredTimer(rs.getLong("session_id"), rs.getLong("chat_id"),
                        rs.getString("title"), rs.getInt("planned_minutes")))
                .list();
    }

    /** Marca o aviso da sessão como enviado, para não repetir na próxima verificação. */
    public void markNotified(long sessionId, Instant now) {
        jdbc.sql("UPDATE task_sessions SET expiry_notified_at = :now WHERE id = :id")
                .param("now", utc(now))
                .param("id", sessionId)
                .update();
    }

    /** Um timer que acabou: para quem avisar e o que dizer. */
    public record ExpiredTimer(long sessionId, long chatId, String taskTitle, int plannedMinutes) {
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
