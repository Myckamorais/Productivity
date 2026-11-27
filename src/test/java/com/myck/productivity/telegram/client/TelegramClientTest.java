package com.myck.productivity.telegram.client;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fica no mesmo pacote do cliente para testar a máscara do token, que não é pública. */
class TelegramClientTest {

    private static final String TOKEN = "123456:SEGREDO";

    private static TelegramClient cliente(String token, String apiUrl) {
        return new TelegramClient(new TelegramProperties(token, "tarefanoar_bot", "webhook", null, null,
                apiUrl, Duration.ofMinutes(1)));
    }

    private static TelegramClient cliente(String token) {
        return cliente(token, "https://api.telegram.org");
    }

    @Test
    void deveMascararOTokenNaMensagemDeErro() {
        // Exceções de rede costumam trazer a URL, e o token faz parte dela
        String erro = "I/O error on POST request for \"https://api.telegram.org/bot123456:SEGREDO/sendMessage\"";

        String mascarado = cliente(TOKEN).mask(erro);

        assertThat(mascarado).doesNotContain("SEGREDO").contains("/bot***/sendMessage");
    }

    @Test
    void deveManterOTextoQuandoNaoHaTokenOuTexto() {
        assertThat(cliente("").mask("sem token")).isEqualTo("sem token");
        assertThat(cliente("abc").mask(null)).isNull();
    }

    @Test
    void naoDeveTentarEnviarNemFalharComOBotDesligado() {
        // Sem token não há para onde mandar: a chamada não acontece e não lança erro
        assertThatCode(() -> cliente("").sendMessage(1, "oi")).doesNotThrowAnyException();
    }

    /**
     * Chamadas HTTP de verdade contra um "Telegram falso" local (o HttpServer do próprio JDK). Nos outros testes
     * o cliente é mockado; aqui se confere a URL, o JSON enviado e a leitura das respostas reais do Telegram.
     */
    @Nested
    class ContraUmTelegramFalso {

        private HttpServer servidor;
        private String apiUrl;

        // O que o servidor recebeu na última chamada e o que ele vai responder
        private String caminhoRecebido;
        private String corpoRecebido;
        private int statusDaResposta = 200;
        private String corpoDaResposta = "{\"ok\": true, \"result\": true}";

        @BeforeEach
        void subirServidor() throws IOException {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", troca -> {
                caminhoRecebido = troca.getRequestURI().getPath();
                corpoRecebido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] resposta = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(statusDaResposta, resposta.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(resposta);
                }
            });
            servidor.start();
            apiUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        }

        @AfterEach
        void derrubarServidor() {
            servidor.stop(0);
        }

        @Test
        void deveEnviarAMensagemParaOMetodoCertoComOChatEOTexto() {
            cliente(TOKEN, apiUrl).sendMessage(555, "Olá! ✅");

            assertThat(caminhoRecebido).isEqualTo("/bot" + TOKEN + "/sendMessage");
            assertThat(((Number) JsonPath.read(corpoRecebido, "$.chat_id")).longValue()).isEqualTo(555L);
            assertThat((String) JsonPath.read(corpoRecebido, "$.text")).isEqualTo("Olá! ✅");
        }

        @Test
        void deveLerAsAtualizacoesNoFormatoRealDoTelegramIgnorandoOsCamposQueNaoUsa() {
            // Given: resposta como a do Telegram, com vários campos que o bot não usa (from, date, entities...)
            corpoDaResposta = """
                    {"ok": true, "result": [
                      {"update_id": 900, "message": {"message_id": 7, "date": 1764090000,
                        "from": {"id": 555, "is_bot": false, "first_name": "Ana", "language_code": "pt-br"},
                        "chat": {"id": 555, "first_name": "Ana", "type": "private"},
                        "text": "/hoje", "entities": [{"offset": 0, "length": 5, "type": "bot_command"}]}},
                      {"update_id": 901, "edited_message": {"message_id": 8}}
                    ]}""";

            // When
            List<TelegramUpdate> atualizacoes = cliente(TOKEN, apiUrl).getUpdates(900);

            // Then: pede a partir do offset, com espera longa e só mensagens
            assertThat(caminhoRecebido).isEqualTo("/bot" + TOKEN + "/getUpdates");
            assertThat(((Number) JsonPath.read(corpoRecebido, "$.offset")).longValue()).isEqualTo(900L);
            assertThat((Integer) JsonPath.read(corpoRecebido, "$.timeout")).isEqualTo(TelegramClient.LONG_POLL_SECONDS);
            assertThat(atualizacoes).containsExactly(
                    new TelegramUpdate(900, new TelegramUpdate.Message(7, new TelegramUpdate.Chat(555, "private"), "/hoje")),
                    new TelegramUpdate(901, null));
        }

        @Test
        void deveDevolverListaVaziaQuandoOTelegramNaoMandaResultado() {
            corpoDaResposta = "{\"ok\": true}";

            assertThat(cliente(TOKEN, apiUrl).getUpdates(0)).isEmpty();
        }

        @Test
        void deveRegistrarOWebhookComOSegredo() {
            cliente(TOKEN, apiUrl).setWebhook("https://api.exemplo.com/telegram/webhook", "segredo-do-webhook");

            assertThat(caminhoRecebido).isEqualTo("/bot" + TOKEN + "/setWebhook");
            assertThat((String) JsonPath.read(corpoRecebido, "$.url")).isEqualTo("https://api.exemplo.com/telegram/webhook");
            assertThat((String) JsonPath.read(corpoRecebido, "$.secret_token")).isEqualTo("segredo-do-webhook");
        }

        @Test
        void deveRemoverOWebhook() {
            cliente(TOKEN, apiUrl).deleteWebhook();

            assertThat(caminhoRecebido).isEqualTo("/bot" + TOKEN + "/deleteWebhook");
        }

        @Test
        void deveFalharComADescricaoDoTelegramQuandoEleRecusaAChamada() {
            // O Telegram responde 200 com ok=false em vários erros de negócio
            corpoDaResposta = "{\"ok\": false, \"description\": \"Bad Request: chat not found\"}";

            assertThatThrownBy(() -> cliente(TOKEN, apiUrl).sendMessage(1, "oi"))
                    .isInstanceOf(TelegramClient.TelegramException.class)
                    .hasMessageContaining("Telegram recusou sendMessage")
                    .hasMessageContaining("chat not found");
        }

        @Test
        void deveFalharSemVazarOTokenQuandoOTelegramRespondeErroHttp() {
            statusDaResposta = 401;
            corpoDaResposta = "{\"ok\": false, \"error_code\": 401, \"description\": \"Unauthorized\"}";

            assertThatThrownBy(() -> cliente(TOKEN, apiUrl).deleteWebhook())
                    .isInstanceOf(TelegramClient.TelegramException.class)
                    .hasMessageContaining("Falha ao chamar deleteWebhook")
                    .hasMessageNotContaining("SEGREDO");
        }

        @Test
        void deveFalharSemVazarOTokenQuandoOTelegramEstaForaDoAr() {
            // Given: ninguém escutando na porta; a exceção de rede original traz a URL com o token
            servidor.stop(0);

            // When / Then
            assertThatThrownBy(() -> cliente(TOKEN, apiUrl).sendMessage(1, "oi"))
                    .isInstanceOf(TelegramClient.TelegramException.class)
                    .hasMessageContaining("Falha ao chamar sendMessage")
                    .hasMessageNotContaining("SEGREDO");
        }
    }
}
