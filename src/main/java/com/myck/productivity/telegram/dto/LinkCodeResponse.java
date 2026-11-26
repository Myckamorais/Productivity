package com.myck.productivity.telegram.dto;

import java.time.Instant;

/**
 * Código para vincular a conta ao bot.
 *
 * @param code      enviar ao bot como "/vincular CÓDIGO"
 * @param expiresAt até quando o código vale
 * @param deepLink  link que abre o bot já com o código (t.me/&lt;bot&gt;?start=CÓDIGO): basta tocar em "Iniciar"
 */
public record LinkCodeResponse(String code, Instant expiresAt, String deepLink) {
}
