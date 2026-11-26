package com.myck.productivity.telegram.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente mínimo da API de bots do Telegram (sem biblioteca): só as 4 chamadas que o bot usa.
 *
 * O token faz parte da URL (api.telegram.org/bot&lt;TOKEN&gt;/...), e as exceções de rede costumam trazer a URL
 * na mensagem. Por isso nenhuma exceção sai daqui com a mensagem original: tudo é registrado com o token
 * mascarado, para ele nunca ir parar nos logs.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    /** Tempo que o getUpdates espera por mensagens novas antes de responder vazio (long polling). */
    public static final int LONG_POLL_SECONDS = 25;

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // A leitura precisa esperar mais que o long polling, senão todo getUpdates vazio viraria erro
        requestFactory.setReadTimeout(Duration.ofSeconds(LONG_POLL_SECONDS + 10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Envia uma mensagem de texto simples (sem formatação, para não precisar escapar caracteres). */
    public void sendMessage(long chatId, String text) {
        if (!properties.enabled()) {
            log.debug("Bot do Telegram desligado; mensagem para o chat {} não enviada", chatId);
            return;
        }
        call("sendMessage", Map.of("chat_id", chatId, "text", text), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Busca as atualizações a partir de {@code offset} (o Telegram descarta as anteriores), esperando até
     * {@link #LONG_POLL_SECONDS} segundos por novidades.
     */
    public List<TelegramUpdate> getUpdates(long offset) {
        TelegramResponse<List<TelegramUpdate>> response = call("getUpdates",
                Map.of("offset", offset, "timeout", LONG_POLL_SECONDS, "allowed_updates", List.of("message")),
                new ParameterizedTypeReference<>() {
                });
        return response.result() == null ? List.of() : response.result();
    }

    /** Registra a URL do webhook e o segredo que o Telegram devolverá no header de cada chamada. */
    public void setWebhook(String url, String secret) {
        call("setWebhook", Map.of("url", url, "secret_token", secret, "allowed_updates", List.of("message")),
                new ParameterizedTypeReference<TelegramResponse<Boolean>>() {
                });
    }

    /** Remove o webhook: enquanto ele existe, o Telegram recusa o getUpdates (modo polling). */
    public void deleteWebhook() {
        call("deleteWebhook", Map.of(), new ParameterizedTypeReference<TelegramResponse<Boolean>>() {
        });
    }

    private <T> TelegramResponse<T> call(String method, Map<String, Object> body,
            ParameterizedTypeReference<TelegramResponse<T>> type) {
        try {
            TelegramResponse<T> response = restClient.post()
                    .uri(properties.apiUrl() + "/bot" + properties.token() + "/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (response == null || !response.ok()) {
                throw new TelegramException("Telegram recusou " + method + ": "
                        + (response == null ? "resposta vazia" : response.description()));
            }
            return response;
        } catch (TelegramException e) {
            throw e;
        } catch (RuntimeException e) {
            // Mensagem original descartada de propósito: pode conter a URL com o token
            throw new TelegramException("Falha ao chamar " + method + " no Telegram: "
                    + mask(e.getClass().getSimpleName() + " " + e.getMessage()));
        }
    }

    /** Troca o token por *** em qualquer texto que vá para log ou exceção. */
    String mask(String text) {
        if (text == null || !properties.enabled()) {
            return text;
        }
        return text.replace(properties.token(), "***");
    }

    /** Envelope padrão das respostas do Telegram: {"ok": true, "result": ...} ou {"ok": false, "description": ...}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramResponse<T>(boolean ok, T result, @JsonProperty("description") String description) {
    }

    /** Falha numa chamada ao Telegram, já com o token mascarado. */
    public static class TelegramException extends RuntimeException {

        public TelegramException(String message) {
            super(message);
        }
    }
}
