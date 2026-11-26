package com.myck.productivity.task;

import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.entity.TimerBlocks;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimerBlocksTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T15:00:00Z");

    private static Instant antes(long minutos) {
        return AGORA.minus(Duration.ofMinutes(minutos));
    }

    @Test
    void deveRetornarVazioQuandoNaoHaSessoes() {
        assertThat(TimerBlocks.remaining(List.of(), AGORA)).isEmpty();
    }

    @Test
    void deveRetornarVazioQuandoASessaoMaisRecenteEDeCronometroLivre() {
        TaskSession livre = TaskSessionFactory.fechada(1L, 7L, antes(30), antes(20));

        assertThat(TimerBlocks.remaining(List.of(livre), AGORA)).isEmpty();
    }

    @Test
    void deveDescontarOTempoJaUsadoDoBloco() {
        // 30 min escolhidos, 10 usados: restam 20
        UUID bloco = UUID.randomUUID();
        TaskSession sessao = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, bloco);

        assertThat(TimerBlocks.remaining(List.of(sessao), AGORA)).contains(Duration.ofMinutes(20));
    }

    @Test
    void deveSomarAsVariasSessoesDoMesmoBloco() {
        // 15 min escolhidos, pausado duas vezes (5 + 4 min): restam 6
        UUID bloco = UUID.randomUUID();
        TaskSession primeira = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(55), 15, bloco);
        TaskSession segunda = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(40), antes(36), 15, bloco);

        assertThat(TimerBlocks.remaining(List.of(primeira, segunda), AGORA)).contains(Duration.ofMinutes(6));
    }

    @Test
    void deveContarAParteJaDecorridaDaSessaoRodando() {
        // 30 min escolhidos: 10 numa sessão fechada + 5 na que está rodando
        UUID bloco = UUID.randomUUID();
        TaskSession fechada = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, bloco);
        TaskSession rodando = new TaskSession(1L, 7L, antes(5), 30, bloco, Duration.ofMinutes(20));

        assertThat(TimerBlocks.remaining(List.of(fechada, rodando), AGORA)).contains(Duration.ofMinutes(15));
    }

    @Test
    void deveIgnorarSessoesDeOutrosBlocos() {
        // O bloco antigo (15 min, todo usado) não interfere no bloco novo (30 min, 10 usados)
        TaskSession antigo = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(120), antes(105), 15, UUID.randomUUID());
        TaskSession novo = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, UUID.randomUUID());

        assertThat(TimerBlocks.remaining(List.of(antigo, novo), AGORA)).contains(Duration.ofMinutes(20));
    }

    @Test
    void deveIgnorarSessoesLivresAnterioresAoBloco() {
        TaskSession livre = TaskSessionFactory.fechada(1L, 7L, antes(120), antes(60));
        TaskSession bloco = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(50), antes(45), 15, UUID.randomUUID());

        assertThat(TimerBlocks.remaining(List.of(livre, bloco), AGORA)).contains(Duration.ofMinutes(10));
    }

    @Test
    void deveRetornarZeroQuandoOBlocoFoiTodoUsado() {
        TaskSession sessao = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(30), antes(15), 15, UUID.randomUUID());

        assertThat(TimerBlocks.remaining(List.of(sessao), AGORA)).contains(Duration.ZERO);
    }

    @Test
    void deveRetornarZeroQuandoASessaoAbertaJaPassouDoLimite() {
        // Aberta há 50 min com limite de 15: o tempo parou no limite e nada resta
        TaskSession vencida = TaskSessionFactory.aberta(1L, 7L, antes(50), 15);

        assertThat(TimerBlocks.remaining(List.of(vencida), AGORA)).contains(Duration.ZERO);
    }
}
