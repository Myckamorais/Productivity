package com.myck.productivity.telegram.service;

import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Modo polling (perfil dev): sem URL pública para o webhook, a própria API pergunta ao Telegram se há
 * mensagens novas (getUpdates com espera longa) numa thread virtual. Em produção fica desligado e o
 * Telegram chama o webhook.
 */
@Component
public class TelegramPollingRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingRunner.class);
    private static final long RETRY_DELAY_MILLIS = 5_000;

    private final TelegramClient client;
    private final TelegramBotService botService;
    private final TelegramProperties properties;

    private volatile boolean running;
    private Thread thread;
    // Próxima atualização esperada: o Telegram descarta tudo antes dela, então nada é tratado duas vezes
    private long offset;

    public TelegramPollingRunner(TelegramClient client, TelegramBotService botService, TelegramProperties properties) {
        this.client = client;
        this.botService = botService;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.enabled() || !properties.pollingMode()) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("telegram-polling").start(this::loop);
        log.info("Bot do Telegram em modo polling");
    }

    private void loop() {
        try {
            // Com webhook registrado, o Telegram recusa o getUpdates
            client.deleteWebhook();
        } catch (TelegramClient.TelegramException e) {
            log.warn("Não foi possível remover o webhook: {}", e.getMessage());
        }
        while (running) {
            try {
                pollOnce();
            } catch (RuntimeException e) {
                log.warn("Falha ao buscar mensagens do Telegram; nova tentativa em 5 s: {}", e.getMessage());
                pause();
            }
        }
    }

    /** Uma rodada: busca as mensagens novas, trata cada uma e avança o offset. Erro numa mensagem não para as outras. */
    void pollOnce() {
        for (TelegramUpdate update : client.getUpdates(offset)) {
            offset = Math.max(offset, update.updateId() + 1);
            try {
                botService.handle(update);
            } catch (RuntimeException e) {
                log.error("Erro ao tratar a atualização {} do Telegram", update.updateId(), e);
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
