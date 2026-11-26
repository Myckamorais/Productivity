package com.myck.productivity.telegram;

import com.myck.productivity.telegram.service.BotCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotCommandTest {

    @Test
    void deveLerComandoSemArgumentos() {
        assertThat(BotCommand.parse("/hoje")).isEqualTo(new BotCommand("hoje", List.of()));
    }

    @Test
    void deveLerOsArgumentosSeparadosPorEspacoOuVirgula() {
        assertThat(BotCommand.parse("/concluir 1 3").args()).containsExactly("1", "3");
        assertThat(BotCommand.parse("/concluir 1,3, 5").args()).containsExactly("1", "3", "5");
        assertThat(BotCommand.parse("/concluir   2   ").args()).containsExactly("2");
    }

    @Test
    void deveIgnorarONomeDoBotQueOTelegramAcrescenta() {
        assertThat(BotCommand.parse("/hoje@tarefanoar_bot").name()).isEqualTo("hoje");
        assertThat(BotCommand.parse("/concluir@tarefanoar_bot 2").args()).containsExactly("2");
    }

    @Test
    void deveIgnorarMaiusculasNoNomeDoComando() {
        assertThat(BotCommand.parse("/HOJE").name()).isEqualTo("hoje");
    }

    @Test
    void deveLerOCodigoDoStart() {
        BotCommand comando = BotCommand.parse("/start ABCD2345");

        assertThat(comando.name()).isEqualTo("start");
        assertThat(comando.args()).containsExactly("ABCD2345");
    }
}
