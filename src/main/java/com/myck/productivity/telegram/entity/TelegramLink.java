package com.myck.productivity.telegram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Vínculo entre um usuário e a conversa dele com o bot. Um chat por usuário e um usuário por chat. */
@Entity
@Table(name = "telegram_links")
@Getter
@Setter
@NoArgsConstructor
public class TelegramLink {

    // O próprio usuário é a chave: no máximo um vínculo por usuário
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    // Categoria onde o bot cria as tarefas recebidas por texto; nula até o primeiro uso
    @Column(name = "default_category_id")
    private Long defaultCategoryId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    public TelegramLink(Long userId) {
        this.userId = userId;
    }

    /** Liga o usuário a este chat agora (truncado em microssegundos, a precisão do PostgreSQL). */
    public void linkTo(long chatId, Instant now) {
        this.chatId = chatId;
        this.linkedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
}
