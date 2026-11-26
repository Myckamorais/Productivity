package com.myck.productivity.telegram.controller;

import com.myck.productivity.common.BusinessException;
import com.myck.productivity.telegram.client.TelegramProperties;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import com.myck.productivity.telegram.service.TelegramBotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class TelegramWebhookController {

    /** Header em que o Telegram devolve o segredo informado no setWebhook. */
    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramBotService botService;
    private final TelegramProperties properties;

    public TelegramWebhookController(TelegramBotService botService, TelegramProperties properties) {
        this.botService = botService;
        this.properties = properties;
    }

    /**
     * Recebe as mensagens do bot enviadas pelo Telegram e responde 200 sem corpo. A rota é pública (o Telegram
     * não tem JWT), então só aceita chamadas com o segredo certo no header; qualquer outra recebe 401.
     */
    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody TelegramUpdate update) {
        if (!properties.hasWebhookSecret() || !sameSecret(secret, properties.webhookSecret())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Segredo do webhook inválido.");
        }
        botService.handle(update);
        return ResponseEntity.ok().build();
    }

    // Comparação em tempo constante: não revela, pelo tempo de resposta, quantos caracteres estavam certos
    private static boolean sameSecret(String received, String expected) {
        return received != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
