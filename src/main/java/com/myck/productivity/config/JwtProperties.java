package com.myck.productivity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT (prefixo app.jwt no application.yml).
 *
 * @param secret            segredo de assinatura HMAC; vem de variável de ambiente
 * @param expirationMinutes validade do token de acesso, em minutos
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
