package com.myck.productivity.telegram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Código temporário de vinculação: gerado no app, enviado ao bot e apagado assim que usado ou vencido. */
@Entity
@Table(name = "telegram_link_codes")
@Getter
@NoArgsConstructor
public class TelegramLinkCode {

    @Id
    private String code;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public TelegramLinkCode(String code, Long userId, Instant expiresAt) {
        this.code = code;
        this.userId = userId;
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
