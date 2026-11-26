package com.myck.productivity.telegram;

import com.myck.productivity.telegram.service.TaskListParser;
import com.myck.productivity.telegram.service.TaskListParser.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TaskListParserTest {

    @Test
    void deveCriarUmaTarefaPorLinha() {
        Result resultado = TaskListParser.parse("Estudar Spring\nIr à academia\nLer artigo");

        assertThat(resultado.titles()).containsExactly("Estudar Spring", "Ir à academia", "Ler artigo");
        assertThat(resultado.ignored()).isZero();
    }

    @Test
    void deveAceitarUmaUnicaLinhaComoUmaTarefa() {
        assertThat(TaskListParser.parse("Pagar a conta de luz").titles()).containsExactly("Pagar a conta de luz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "- Estudar", "* Estudar", "• Estudar", "– Estudar", "+ Estudar", "> Estudar",
            "1. Estudar", "1) Estudar", "12 - Estudar", "3: Estudar",
            "[ ] Estudar", "[x] Estudar", "[X] Estudar", "[] Estudar",
            "1. [ ] Estudar", "- [x] Estudar", "-Estudar", "1.Estudar"})
    void deveRemoverMarcadoresENumeracaoDoComecoDaLinha(String linha) {
        assertThat(TaskListParser.parse(linha).titles()).containsExactly("Estudar");
    }

    @Test
    void deveManterNumerosEHifensQueFazemParteDoTitulo() {
        Result resultado = TaskListParser.parse("Ler 30 páginas\nReunião às 14h - sala 2\nComprar pão-de-ló");

        assertThat(resultado.titles())
                .containsExactly("Ler 30 páginas", "Reunião às 14h - sala 2", "Comprar pão-de-ló");
    }

    @Test
    void devePularLinhasEmBrancoELinhasSoComMarcador() {
        Result resultado = TaskListParser.parse("\n  \n- Estudar\n-\n•  \n\n1.\nLer\n");

        assertThat(resultado.titles()).containsExactly("Estudar", "Ler");
    }

    @Test
    void deveJuntarEspacosRepetidosETirarEspacosDasPontas() {
        assertThat(TaskListParser.parse("   Estudar     Spring\t Boot   ").titles())
                .containsExactly("Estudar Spring Boot");
    }

    @Test
    void deveAceitarQuebrasDeLinhaDoWindows() {
        assertThat(TaskListParser.parse("Estudar\r\nLer\r\n").titles()).containsExactly("Estudar", "Ler");
    }

    @Test
    void deveCortarTitulosLongosEm150Caracteres() {
        String longo = "a".repeat(200);

        assertThat(TaskListParser.parse(longo).titles().getFirst()).hasSize(TaskListParser.MAX_TITLE_LENGTH);
    }

    @Test
    void deveCriarNoMaximo20EInformarQuantasFicaramDeFora() {
        StringBuilder mensagem = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            mensagem.append("Tarefa ").append(i).append('\n');
        }

        Result resultado = TaskListParser.parse(mensagem.toString());

        assertThat(resultado.titles()).hasSize(20).startsWith("Tarefa 1").endsWith("Tarefa 20");
        assertThat(resultado.ignored()).isEqualTo(5);
    }

    @Test
    void naoDeveContarLinhasVaziasNoLimite() {
        StringBuilder mensagem = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            mensagem.append("Tarefa ").append(i).append("\n\n");
        }

        assertThat(TaskListParser.parse(mensagem.toString()).ignored()).isZero();
    }

    @Test
    void deveDevolverListaVaziaParaTextoVazioOuNulo() {
        assertThat(TaskListParser.parse("").titles()).isEmpty();
        assertThat(TaskListParser.parse("   \n  ").titles()).isEmpty();
        assertThat(TaskListParser.parse(null).titles()).isEmpty();
    }
}
