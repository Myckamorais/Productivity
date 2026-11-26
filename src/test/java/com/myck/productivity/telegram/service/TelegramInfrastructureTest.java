package com.myck.productivity.telegram.service;

import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import com.myck.productivity.telegram.repository.TelegramQueries;
import com.myck.productivity.telegram.repository.TelegramQueries.ExpiredTimer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Partes de infraestrutura do bot: polling, registro do webhook e aviso de fim do timer. */
class TelegramInfrastructureTest {

    private static TelegramProperties propriedades(String token, String modo, String url, String segredo) {
        return new TelegramProperties(token, "tarefanoar_bot", modo, url, segredo, "https://api.telegram.org",
                Duration.ofMinutes(1));
    }

    private static TelegramUpdate atualizacao(long id) {
        return new TelegramUpdate(id, new TelegramUpdate.Message(1, new TelegramUpdate.Chat(9, "private"), "oi"));
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Polling {

        @Mock
        TelegramClient client;
        @Mock
        TelegramBotService bot;

        @Test
        void deveTratarCadaMensagemEAvancarOOffsetParaDepoisDaUltima() {
            var runner = new TelegramPollingRunner(client, bot, propriedades("t", "polling", null, null));
            when(client.getUpdates(0)).thenReturn(List.of(atualizacao(41), atualizacao(42)));

            runner.pollOnce();

            verify(bot).handle(atualizacao(41));
            verify(bot).handle(atualizacao(42));
            // O Telegram descarta tudo antes do offset: a próxima busca não repete as mensagens já tratadas
            when(client.getUpdates(43)).thenReturn(List.of());
            runner.pollOnce();
            verify(client).getUpdates(43);
        }

        @Test
        void deveContinuarAsOutrasMensagensQuandoUmaFalha() {
            var runner = new TelegramPollingRunner(client, bot, propriedades("t", "polling", null, null));
            when(client.getUpdates(0)).thenReturn(List.of(atualizacao(1), atualizacao(2)));
            doThrow(new IllegalStateException("erro")).when(bot).handle(atualizacao(1));

            runner.pollOnce();

            verify(bot).handle(atualizacao(2));
        }

        @Test
        void naoDeveLigarSemTokenOuForaDoModoPolling() {
            new TelegramPollingRunner(client, bot, propriedades("", "polling", null, null)).start();
            new TelegramPollingRunner(client, bot, propriedades("t", "webhook", null, null)).start();

            verifyNoInteractions(client);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class RegistroDoWebhook {

        @Mock
        TelegramClient client;

        @Test
        void deveRegistrarOWebhookComOSegredoNoModoWebhook() {
            new TelegramWebhookRegistrar(client,
                    propriedades("t", "webhook", "https://api.exemplo.com/telegram/webhook", "segredo")).register();

            verify(client).setWebhook("https://api.exemplo.com/telegram/webhook", "segredo");
        }

        @Test
        void naoDeveRegistrarSemSegredo() {
            // Sem segredo, qualquer um conseguiria mandar mensagens falsas ao webhook
            new TelegramWebhookRegistrar(client,
                    propriedades("t", "webhook", "https://api.exemplo.com/telegram/webhook", "")).register();

            verifyNoInteractions(client);
        }

        @Test
        void naoDeveRegistrarSemTokenSemUrlOuNoModoPolling() {
            new TelegramWebhookRegistrar(client, propriedades("", "webhook", "https://x", "s")).register();
            new TelegramWebhookRegistrar(client, propriedades("t", "webhook", "", "s")).register();
            new TelegramWebhookRegistrar(client, propriedades("t", "polling", "https://x", "s")).register();

            verifyNoInteractions(client);
        }

        @Test
        void naoDeveDerrubarAAplicacaoQuandoOTelegramFalha() {
            doThrow(new TelegramClient.TelegramException("fora do ar")).when(client).setWebhook(anyString(), anyString());

            new TelegramWebhookRegistrar(client, propriedades("t", "webhook", "https://x", "s")).register();

            verify(client).setWebhook("https://x", "s");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class AvisoDeFimDoTimer {

        private static final Instant AGORA = Instant.parse("2026-11-25T18:00:00Z");

        @Mock
        TelegramQueries queries;
        @Mock
        TelegramClient client;

        private TimerExpiryNotifier notifier() {
            return new TimerExpiryNotifier(queries, client, propriedades("t", "webhook", null, null),
                    Clock.fixed(AGORA, ZoneOffset.UTC));
        }

        @Test
        void deveAvisarCadaTimerVencidoEMarcarComoAvisado() {
            when(queries.expiredTimersToNotify(AGORA, AGORA.minus(Duration.ofHours(1))))
                    .thenReturn(List.of(new ExpiredTimer(7, 555, "Estudar Spring", 30)));

            int enviados = notifier().notifyExpiredTimers();

            assertThat(enviados).isEqualTo(1);
            verify(client).sendMessage(eq(555L), contains("Acabaram os 30 min de \"Estudar Spring\""));
            verify(queries).markNotified(7, AGORA);
        }

        @Test
        void naoDeveMarcarComoAvisadoQuandoOEnvioFalhaParaTentarDeNovoDepois() {
            when(queries.expiredTimersToNotify(any(), any()))
                    .thenReturn(List.of(new ExpiredTimer(7, 555, "Estudar", 15), new ExpiredTimer(8, 666, "Ler", 15)));
            doThrow(new TelegramClient.TelegramException("rede")).when(client).sendMessage(eq(555L), anyString());

            int enviados = notifier().notifyExpiredTimers();

            // A falha num chat não impede o aviso dos outros
            assertThat(enviados).isEqualTo(1);
            verify(queries, never()).markNotified(eq(7L), any());
            verify(queries).markNotified(8, AGORA);
        }

        @Test
        void naoDeveEnviarNadaQuandoNaoHaTimersVencidos() {
            when(queries.expiredTimersToNotify(any(), any())).thenReturn(List.of());

            assertThat(notifier().notifyExpiredTimers()).isZero();
            verify(client, never()).sendMessage(anyLong(), anyString());
        }
    }
}
