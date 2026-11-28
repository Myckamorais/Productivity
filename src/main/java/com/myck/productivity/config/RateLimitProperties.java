package com.myck.productivity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limites de tentativas no login e no cadastro (prefixo app.rate-limit).
 *
 * @param enabled                desligado só nos testes que fazem muitos cadastros seguidos
 * @param loginPerIpPerMinute    chamadas de login por IP a cada minuto
 * @param registerPerIpPerHour   cadastros por IP a cada hora (barra criação de contas em massa)
 * @param loginFailuresPerEmail  senhas erradas seguidas para o mesmo e-mail antes do bloqueio
 * @param loginLock              quanto tempo o login daquele e-mail fica bloqueado
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int loginPerIpPerMinute, int registerPerIpPerHour,
        int loginFailuresPerEmail, Duration loginLock) {
}
