package com.myck.productivity.telegram.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do bot (prefixo app.telegram no application.yml). Os segredos vêm de variáveis de ambiente.
 *
 * @param token               token do @BotFather; vazio desliga o bot
 * @param botUsername         nome do bot, usado no link de vinculação (t.me/&lt;nome&gt;?start=CÓDIGO)
 * @param mode                {@code webhook} (produção) ou {@code polling} (dev)
 * @param webhookUrl          URL pública do webhook, registrada no Telegram ao subir; vazia não registra
 * @param webhookSecret       segredo conferido em cada chamada do webhook
 * @param apiUrl              endereço da API do Telegram
 * @param expiryCheckInterval intervalo da verificação de timers que acabaram
 */
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(String token, String botUsername, String mode, String webhookUrl,
        String webhookSecret, String apiUrl, Duration expiryCheckInterval) {

    /** O bot só funciona com token configurado. */
    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    public boolean pollingMode() {
        return "polling".equalsIgnoreCase(mode);
    }

    public boolean hasWebhookUrl() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
