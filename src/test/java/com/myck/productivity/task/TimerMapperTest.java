package com.myck.productivity.task;

import com.myck.productivity.task.dto.TimerResponse;
import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.mapper.TimerMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimerMapperTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T15:00:00Z");

    private final TimerMapper mapper = new TimerMapper();

    private static Instant antes(long minutos) {
        return AGORA.minus(Duration.ofMinutes(minutos));
    }

    private static Task tarefa(TaskStatus status) {
        Task task = TaskFactory.estudarSpring(7L, 1L);
        task.changeStatus(status, AGORA);
        return task;
    }

    @Test
    void deveSerIdleQuandoATarefaNuncaFoiIniciada() {
        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.PENDING), List.of(), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.IDLE);
        assertThat(resposta.totalSeconds()).isZero();
        assertThat(resposta.activeSession()).isNull();
        assertThat(resposta.sessions()).isEmpty();
    }

    @Test
    void deveMostrarTempoDecorridoESemRestanteNoCronometroLivre() {
        TaskSession sessao = TaskSessionFactory.aberta(1L, 7L, antes(5), null);

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(sessao), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.activeSession().elapsedSeconds()).isEqualTo(300);
        assertThat(resposta.activeSession().remainingSeconds()).isNull();
        assertThat(resposta.activeSession().expiresAt()).isNull();
    }

    @Test
    void deveMostrarOTempoRestanteQuandoHaDuracaoEscolhida() {
        // 30 minutos escolhidos, 5 já passados: faltam 25
        TaskSession sessao = TaskSessionFactory.aberta(1L, 7L, antes(5), 30);

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(sessao), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.activeSession().plannedMinutes()).isEqualTo(30);
        assertThat(resposta.activeSession().elapsedSeconds()).isEqualTo(300);
        assertThat(resposta.activeSession().remainingSeconds()).isEqualTo(1500);
        assertThat(resposta.activeSession().expiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(25)));
    }

    @Test
    void deveSomarAsSessoesFechadasEAAberta() {
        TaskSession primeira = TaskSessionFactory.fechada(1L, 7L, antes(60), antes(50));   // 10 min
        TaskSession segunda = TaskSessionFactory.fechada(1L, 7L, antes(40), antes(35));    // 5 min
        TaskSession terceira = TaskSessionFactory.aberta(1L, 7L, antes(2), null);          // 2 min

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS),
                List.of(primeira, segunda, terceira), AGORA);

        assertThat(resposta.totalSeconds()).isEqualTo(17 * 60);
        assertThat(resposta.sessions()).extracting(TimerResponse.SessionItem::durationSeconds)
                .containsExactly(600L, 300L, 120L);
        assertThat(resposta.sessions().get(2).endedAt()).isNull();
    }

    @Test
    void deveSerPausadaQuandoATarefaEstaEmAndamentoSemSessaoRodando() {
        TaskSession fechada = TaskSessionFactory.fechada(1L, 7L, antes(30), antes(20));

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(fechada), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.activeSession()).isNull();
        assertThat(resposta.totalSeconds()).isEqualTo(600);
    }

    @Test
    void deveSerPausadaEContarSoOLimiteQuandoOTempoEscolhidoJaAcabou() {
        // Aberta há 50 minutos com limite de 15: ninguém pausou, mas o tempo parou no limite
        TaskSession expirada = TaskSessionFactory.aberta(1L, 7L, antes(50), 15);

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(expirada), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.activeSession()).isNull();
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
        assertThat(resposta.sessions().getFirst().endedAt()).isEqualTo(antes(35));
    }

    @Test
    void deveSerConcluidaQuandoATarefaEstaConcluida() {
        TaskSession fechada = TaskSessionFactory.fechada(1L, 7L, antes(30), antes(10));

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.COMPLETED), List.of(fechada), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.COMPLETED);
        assertThat(resposta.totalSeconds()).isEqualTo(20 * 60);
    }

    @Test
    void deveInformarOTempoRestanteDoTimerMesmoPausado() {
        // 30 min escolhidos, 10 usados e pausado: a pausa preservou os 20 restantes
        TaskSession pausada = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, UUID.randomUUID());

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(pausada), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.remainingSeconds()).isEqualTo(20 * 60);
        assertThat(resposta.activeSession()).isNull();
    }

    @Test
    void deveInformarOTempoRestanteDoBlocoQuandoRodando() {
        // 30 min escolhidos: 10 numa sessão fechada e 5 na rodando, restam 15 (limite da rodando: 20 min a partir do início)
        UUID bloco = UUID.randomUUID();
        TaskSession fechada = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, bloco);
        TaskSession rodando = new TaskSession(1L, 7L, antes(5), 30, bloco, Duration.ofMinutes(20));

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(fechada, rodando), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.remainingSeconds()).isEqualTo(15 * 60);
        // O restante da sessão rodando (até o limite dela) coincide com o do bloco
        assertThat(resposta.activeSession().remainingSeconds()).isEqualTo(15 * 60);
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void deveInformarRestanteZeroQuandoOTempoEscolhidoJaFoiTodoUsado() {
        TaskSession esgotada = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(30), antes(15), 15, UUID.randomUUID());

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(esgotada), AGORA);

        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.remainingSeconds()).isZero();
    }

    @Test
    void naoDeveInformarRestanteNoCronometroLivreNemSemTimerEmCurso() {
        TaskSession livre = TaskSessionFactory.aberta(1L, 7L, antes(5), null);
        TaskSession bloco = TaskSessionFactory.fechadaDeBloco(1L, 7L, antes(60), antes(50), 30, UUID.randomUUID());

        // Cronômetro livre rodando
        assertThat(mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(livre), AGORA).remainingSeconds()).isNull();
        // Tarefa nunca iniciada
        assertThat(mapper.toResponse(tarefa(TaskStatus.PENDING), List.of(), AGORA).remainingSeconds()).isNull();
        // Tarefa concluída, mesmo tendo tido um timer
        assertThat(mapper.toResponse(tarefa(TaskStatus.COMPLETED), List.of(bloco), AGORA).remainingSeconds()).isNull();
    }

    @Test
    void deveCortarOTempoRestanteEmZeroNoMomentoExatoDoLimite() {
        TaskSession sessao = TaskSessionFactory.aberta(1L, 7L, antes(15), 15);

        TimerResponse resposta = mapper.toResponse(tarefa(TaskStatus.IN_PROGRESS), List.of(sessao), AGORA);

        // No instante do limite já não está rodando, e o tempo contado é exatamente 15 minutos
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.totalSeconds()).isEqualTo(900);
    }
}
