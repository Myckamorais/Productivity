package com.myck.productivity.telegram.service;

import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.repository.TelegramQueries;
import com.myck.productivity.telegram.repository.TelegramQueries.ExpiredTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Avisa no Telegram quando o tempo escolhido de um timer acaba. A API não tem como "acordar" sozinha no
 * instante exato, então uma tarefa agendada verifica de tempos em tempos (padrão: a cada minuto); o aviso
 * chega com até esse atraso.
 */
@Component
public class TimerExpiryNotifier {

    private static final Logger log = LoggerFactory.getLogger(TimerExpiryNotifier.class);

    /** Timers vencidos há mais tempo que isso não são avisados (ex.: aplicação ficou desligada). */
    static final Duration MAX_DELAY = Duration.ofHours(1);

    private final TelegramQueries queries;
    private final TelegramClient client;
    private final TelegramProperties properties;
    private final Clock clock;

    public TimerExpiryNotifier(TelegramQueries queries, TelegramClient client, TelegramProperties properties,
            Clock clock) {
        this.queries = queries;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.telegram.expiry-check-interval}",
            initialDelayString = "${app.telegram.expiry-check-interval}")
    void scheduledCheck() {
        if (properties.enabled()) {
            notifyExpiredTimers();
        }
    }

    /**
     * Envia um aviso por timer vencido e o marca como avisado. Só marca depois de enviar: se o Telegram falhar,
     * a próxima verificação tenta de novo (dentro do limite de {@link #MAX_DELAY}). Devolve quantos avisou.
     */
    public int notifyExpiredTimers() {
        Instant now = clock.instant();
        int sent = 0;
        for (ExpiredTimer timer : queries.expiredTimersToNotify(now, now.minus(MAX_DELAY))) {
            try {
                client.sendMessage(timer.chatId(), message(timer));
                queries.markNotified(timer.sessionId(), now);
                sent++;
            } catch (TelegramClient.TelegramException e) {
                log.warn("Não foi possível avisar o fim do timer da sessão {}: {}", timer.sessionId(), e.getMessage());
            }
        }
        return sent;
    }

    static String message(ExpiredTimer timer) {
        return "⏰ Acabaram os " + timer.plannedMinutes() + " min de \"" + timer.taskTitle() + "\".\n"
                + "O cronômetro parou sozinho. No app, retome escolhendo uma nova duração ou finalize a tarefa.";
    }
}
