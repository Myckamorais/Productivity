package com.myck.productivity.telegram.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Fica no mesmo pacote do cliente para testar a máscara do token, que não é pública. */
class TelegramClientTest {

    private static TelegramClient cliente(String token) {
        return new TelegramClient(new TelegramProperties(token, "tarefanoar_bot", "webhook", null, null,
                "https://api.telegram.org", Duration.ofMinutes(1)));
    }

    @Test
    void deveMascararOTokenNaMensagemDeErro() {
        // Exceções de rede costumam trazer a URL, e o token faz parte dela
        String erro = "I/O error on POST request for \"https://api.telegram.org/bot123456:SEGREDO/sendMessage\"";

        String mascarado = cliente("123456:SEGREDO").mask(erro);

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
}
