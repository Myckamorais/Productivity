package com.myck.productivity.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Um período contínuo de trabalho numa tarefa: começa no start/resume e termina no pause/finish.
 * Se uma duração foi escolhida, a sessão expira em {@code expiresAt} (o início mais o que restava do bloco)
 * e o tempo contado nunca passa disso, mesmo que ninguém a feche na hora.
 */
@Entity
@Table(name = "task_sessions")
@Getter
@NoArgsConstructor
public class TaskSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Só os ids (sem @ManyToOne): toda consulta já filtra por usuário ou por tarefa do usuário
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    // Nulo enquanto a sessão está aberta
    @Column(name = "ended_at")
    private Instant endedAt;

    // Duração total escolhida para o bloco (15, 30, 45 ou 60); nula no cronômetro livre. É a mesma em todas as sessões do bloco
    @Column(name = "planned_minutes", updatable = false)
    private Integer plannedMinutes;

    // Identifica o bloco de tempo escolhido: sessões de continuação (resume sem duração) repetem o do bloco.
    // Nulo no cronômetro livre
    @Column(name = "block_id", updatable = false)
    private UUID blockId;

    // Quando ESTA sessão para: início + o tempo que restava do bloco naquele momento (no início do bloco, a
    // duração escolhida inteira; numa continuação, só o restante). Nulo no cronômetro livre
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    /**
     * Abre uma sessão que inicia um bloco novo: com duração escolhida, o bloco tem esse tempo inteiro;
     * sem duração, é cronômetro livre. Os instantes são truncados em microssegundos, a precisão do PostgreSQL.
     */
    public TaskSession(Long userId, Long taskId, Instant startedAt, Integer plannedMinutes) {
        this(userId, taskId, startedAt, plannedMinutes,
                plannedMinutes == null ? null : UUID.randomUUID(),
                plannedMinutes == null ? null : Duration.ofMinutes(plannedMinutes));
    }

    /**
     * Abre uma sessão de um bloco existente (continuação após pausa), com apenas o tempo restante do bloco
     * como limite. Duração, bloco e limite andam juntos: ou os três são informados ou nenhum (cronômetro livre).
     */
    public TaskSession(Long userId, Long taskId, Instant startedAt, Integer plannedMinutes, UUID blockId,
            Duration allowance) {
        if ((plannedMinutes == null) != (blockId == null) || (plannedMinutes == null) != (allowance == null)) {
            throw new IllegalArgumentException("Duração escolhida, bloco e tempo permitido devem vir juntos ou nenhum.");
        }
        this.userId = userId;
        this.taskId = taskId;
        this.startedAt = startedAt.truncatedTo(ChronoUnit.MICROS);
        this.plannedMinutes = plannedMinutes;
        this.blockId = blockId;
        // Também em microssegundos: um limite com nanossegundos seria arredondado pelo banco e o total somaria 0,000001 s a menos
        this.expiresAt = allowance == null ? null : this.startedAt.plus(allowance).truncatedTo(ChronoUnit.MICROS);
    }

    /** A sessão está rodando agora: aberta e, se tem duração escolhida, ainda dentro do tempo. */
    public boolean isActiveAt(Instant now) {
        return endedAt == null && (expiresAt == null || now.isBefore(expiresAt));
    }

    /**
     * Quando a sessão efetivamente terminou: o fim gravado, ou o limite de tempo se ele já passou,
     * ou nulo se ainda está rodando. É isso que impede o tempo de passar do escolhido.
     */
    public Instant effectiveEnd(Instant now) {
        if (endedAt != null) {
            return endedAt;
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return expiresAt;
        }
        return null;
    }

    /** Tempo trabalhado na sessão até {@code now}, sem passar do limite escolhido. */
    public Duration durationAt(Instant now) {
        Instant end = effectiveEnd(now);
        Duration duration = Duration.between(startedAt, end != null ? end : now);
        // Relógio que voltou (ajuste de horário) nunca gera tempo negativo
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    /** Fecha a sessão. Se o tempo escolhido já tinha acabado, o fim fica no limite e não em "agora". Não faz nada se já fechada. */
    public void stop(Instant now) {
        if (endedAt != null) {
            return;
        }
        Instant end = effectiveEnd(now);
        Instant resolved = end != null ? end : now;
        // Garante ended_at >= started_at (exigido pelo banco) mesmo com relógio atrasado
        this.endedAt = (resolved.isBefore(startedAt) ? startedAt : resolved).truncatedTo(ChronoUnit.MICROS);
    }
}
