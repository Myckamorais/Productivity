package com.myck.productivity.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Atualização enviada pelo Telegram (webhook ou getUpdates). Só os campos que o bot usa; o resto do JSON é
 * ignorado, para mudanças da API do Telegram não quebrarem a leitura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        Message message) {

    /** Mensagem recebida; {@code text} é nulo em mensagens sem texto (foto, figurinha, áudio...). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") long messageId,
            Chat chat,
            String text) {
    }

    /** Conversa; {@code type} é "private" numa conversa direta com o bot. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
    }
}
