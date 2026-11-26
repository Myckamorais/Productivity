package com.myck.productivity.task;

import com.myck.productivity.task.entity.TaskSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskSessionTest {

    private static final Instant INICIO = Instant.parse("2026-11-25T15:00:00Z");

    private static Instant depois(long minutos) {
        return INICIO.plus(Duration.ofMinutes(minutos));
    }

    @Test
    void deveCalcularAExpiracaoQuandoHaDuracaoEscolhida() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 30);

        assertThat(sessao.getPlannedMinutes()).isEqualTo(30);
        assertThat(sessao.getExpiresAt()).isEqualTo(depois(30));
    }

    @Test
    void naoDeveTerExpiracaoNoCronometroLivre() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, null);

        assertThat(sessao.getPlannedMinutes()).isNull();
        assertThat(sessao.getExpiresAt()).isNull();
    }

    @Test
    void deveGerarUmBlocoNovoParaCadaInicioComDuracaoESemBlocoNoCronometroLivre() {
        TaskSession primeira = new TaskSession(1L, 2L, INICIO, 30);
        TaskSession segunda = new TaskSession(1L, 2L, INICIO, 30);
        TaskSession livre = new TaskSession(1L, 2L, INICIO, null);

        assertThat(primeira.getBlockId()).isNotNull();
        assertThat(segunda.getBlockId()).isNotNull().isNotEqualTo(primeira.getBlockId());
        assertThat(livre.getBlockId()).isNull();
    }

    @Test
    void deveContinuarOMesmoBlocoLimitadoAoTempoRestante() {
        // Bloco de 30 min do qual já sobraram só 12: a nova sessão expira em início + 12, e não + 30
        UUID bloco = UUID.randomUUID();

        TaskSession continuacao = new TaskSession(1L, 2L, INICIO, 30, bloco, Duration.ofMinutes(12));

        assertThat(continuacao.getBlockId()).isEqualTo(bloco);
        assertThat(continuacao.getPlannedMinutes()).isEqualTo(30);
        assertThat(continuacao.getExpiresAt()).isEqualTo(depois(12));
        assertThat(continuacao.durationAt(depois(50))).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    void deveTruncarOLimiteDaContinuacaoEmMicrossegundos() {
        Duration restante = Duration.ofSeconds(600, 123_456_789);

        TaskSession continuacao = new TaskSession(1L, 2L, INICIO, 15, UUID.randomUUID(), restante);

        assertThat(continuacao.getExpiresAt()).isEqualTo(INICIO.plusSeconds(600).plusNanos(123_456_000));
    }

    @Test
    void deveRecusarCombinacaoIncoerenteDeDuracaoBlocoELimite() {
        UUID bloco = UUID.randomUUID();
        Duration limite = Duration.ofMinutes(10);

        assertThatThrownBy(() -> new TaskSession(1L, 2L, INICIO, 30, null, limite))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaskSession(1L, 2L, INICIO, null, bloco, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaskSession(1L, 2L, INICIO, 30, bloco, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveTruncarOInicioEmMicrossegundos() {
        // O PostgreSQL guarda microssegundos; mais precisão faria a criação divergir das leituras
        Instant comNanos = Instant.parse("2026-11-25T15:00:00.123456789Z");

        TaskSession sessao = new TaskSession(1L, 2L, comNanos, null);

        assertThat(sessao.getStartedAt()).isEqualTo(Instant.parse("2026-11-25T15:00:00.123456Z"));
    }

    @Test
    void deveEstarAtivaEnquantoOTempoEscolhidoNaoAcabou() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 15);

        assertThat(sessao.isActiveAt(depois(0))).isTrue();
        assertThat(sessao.isActiveAt(depois(14))).isTrue();
    }

    @Test
    void deveDeixarDeEstarAtivaExatamenteNoLimite() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 15);

        assertThat(sessao.isActiveAt(depois(15))).isFalse();
        assertThat(sessao.isActiveAt(depois(40))).isFalse();
    }

    @Test
    void deveEstarSempreAtivaNoCronometroLivreEnquantoAberto() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, null);

        assertThat(sessao.isActiveAt(depois(600))).isTrue();
    }

    @Test
    void deveContarOTempoDecorridoQuandoAindaEstaRodando() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 30);

        assertThat(sessao.durationAt(depois(10))).isEqualTo(Duration.ofMinutes(10));
        assertThat(sessao.effectiveEnd(depois(10))).isNull();
    }

    @Test
    void devePararDeContarNoLimiteMesmoSemTerSidoFechada() {
        // Aberta há 50 minutos com limite de 30: só 30 contam
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 30);

        assertThat(sessao.durationAt(depois(50))).isEqualTo(Duration.ofMinutes(30));
        assertThat(sessao.effectiveEnd(depois(50))).isEqualTo(depois(30));
    }

    @Test
    void deveContarTodoOTempoNoCronometroLivre() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, null);

        assertThat(sessao.durationAt(depois(125))).isEqualTo(Duration.ofMinutes(125));
    }

    @Test
    void deveFecharAgoraQuandoOTempoNaoAcabou() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 30);

        sessao.stop(depois(10));

        assertThat(sessao.getEndedAt()).isEqualTo(depois(10));
        assertThat(sessao.durationAt(depois(500))).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void deveFecharNoLimiteQuandoOTempoJaTinhaAcabado() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, 15);

        sessao.stop(depois(40));

        assertThat(sessao.getEndedAt()).isEqualTo(depois(15));
    }

    @Test
    void naoDeveAlterarOFimQuandoJaEstavaFechada() {
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, null);
        sessao.stop(depois(10));

        sessao.stop(depois(99));

        assertThat(sessao.getEndedAt()).isEqualTo(depois(10));
    }

    @Test
    void naoDeveGerarTempoNegativoQuandoORelogioVoltou() {
        // Ajuste de horário do servidor: "agora" antes do início
        TaskSession sessao = new TaskSession(1L, 2L, INICIO, null);
        Instant antes = INICIO.minusSeconds(30);

        assertThat(sessao.durationAt(antes)).isEqualTo(Duration.ZERO);
        sessao.stop(antes);
        assertThat(sessao.getEndedAt()).isEqualTo(INICIO);
    }
}
