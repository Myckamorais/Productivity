package com.myck.productivity.telegram.service;

import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.client.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Em produção (modo webhook), informa ao Telegram a URL do webhook e o segredo assim que a aplicação sobe.
 * Falhar aqui não derruba a aplicação: só o bot fica sem receber mensagens, e o erro vai para o log.
 */
@Component
public class TelegramWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramClient client;
    private final TelegramProperties properties;

    public TelegramWebhookRegistrar(TelegramClient client, TelegramProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!properties.enabled() || properties.pollingMode() || !properties.hasWebhookUrl()) {
            return;
        }
        // Sem segredo, qualquer um poderia mandar mensagens falsas ao webhook; então nem registra
        if (!properties.hasWebhookSecret()) {
            log.error("TELEGRAM_WEBHOOK_SECRET não configurado: webhook do Telegram não registrado");
            return;
        }
        try {
            client.setWebhook(properties.webhookUrl(), properties.webhookSecret());
            log.info("Webhook do Telegram registrado");
        } catch (TelegramClient.TelegramException e) {
            log.error("Não foi possível registrar o webhook do Telegram: {}", e.getMessage());
        }
    }
}
