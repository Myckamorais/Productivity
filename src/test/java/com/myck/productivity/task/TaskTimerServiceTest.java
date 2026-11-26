package com.myck.productivity.task;

import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.task.dto.TimerResponse;
import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.mapper.TimerMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskSessionRepository;
import com.myck.productivity.task.service.TaskTimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskTimerServiceTest {

    private static final Long USER = 1L;
    private static final Long OUTRO_USER = 2L;
    private static final Long TASK = 7L;
    private static final Long OUTRA_TASK = 99L;
    private static final Instant AGORA = Instant.parse("2026-11-25T15:00:00Z");

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskSessionRepository sessionRepository;

    private TaskTimerService timerService;

    @BeforeEach
    void setUp() {
        timerService = new TaskTimerService(taskRepository, sessionRepository, new TimerMapper(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Instant antes(long minutos) {
        return AGORA.minus(Duration.ofMinutes(minutos));
    }

    /** Tarefa do usuário no status informado, já "encontrada" pelo repositório. */
    private Task tarefa(TaskStatus status) {
        Task task = TaskFactory.estudarSpring(TASK, USER);
        task.changeStatus(status, AGORA);
        when(taskRepository.findByIdAndUserId(TASK, USER)).thenReturn(Optional.of(task));
        return task;
    }

    /** Simula as sessões já existentes da tarefa. */
    private void sessoesDaTarefa(TaskSession... sessoes) {
        when(sessionRepository.findAllByTaskIdOrderByStartedAtAsc(TASK)).thenReturn(List.of(sessoes));
    }

    /** Simula a sessão aberta do usuário (em qualquer tarefa). */
    private void sessaoAbertaDoUsuario(TaskSession sessao) {
        when(sessionRepository.findByUserIdAndEndedAtIsNull(USER)).thenReturn(Optional.of(sessao));
    }

    /**
     * Simula uma sessão vencida: a primeira consulta a encontra (e o service a fecha no limite); na segunda,
     * o banco real já não a devolveria, pois passou a ter ended_at. O mock precisa reproduzir isso.
     */
    private void sessaoVencidaDoUsuario(TaskSession vencida) {
        when(sessionRepository.findByUserIdAndEndedAtIsNull(USER)).thenReturn(Optional.of(vencida), Optional.empty());
    }

    // ---------- start ----------

    @Test
    void deveIniciarCronometroLivreEColocarATarefaEmAndamento() {
        // Given
        Task task = tarefa(TaskStatus.PENDING);

        // When
        TimerResponse resposta = timerService.start(USER, TASK, null);

        // Then
        ArgumentCaptor<TaskSession> salva = ArgumentCaptor.forClass(TaskSession.class);
        verify(sessionRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getUserId()).isEqualTo(USER);
        assertThat(salva.getValue().getTaskId()).isEqualTo(TASK);
        assertThat(salva.getValue().getStartedAt()).isEqualTo(AGORA);
        assertThat(salva.getValue().getPlannedMinutes()).isNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository).save(task);
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.activeSession().remainingSeconds()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 45, 60})
    void deveIniciarComADuracaoEscolhidaEInformarOTempoRestante(int minutos) {
        // Given
        tarefa(TaskStatus.PENDING);

        // When
        TimerResponse resposta = timerService.start(USER, TASK, minutos);

        // Then
        assertThat(resposta.activeSession().plannedMinutes()).isEqualTo(minutos);
        assertThat(resposta.activeSession().expiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(minutos)));
        assertThat(resposta.activeSession().remainingSeconds()).isEqualTo(minutos * 60L);
    }

    @Test
    void deveLancarNaoEncontradoAoIniciarTarefaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(TASK, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> timerService.start(OUTRO_USER, TASK, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarIniciarTarefaConcluida() {
        tarefa(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> timerService.start(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("concluída");
                });
    }

    @Test
    void deveRecusarIniciarTarefaJaIniciadaIndicandoOResume() {
        tarefa(TaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> timerService.start(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("resume");
                });
    }

    @Test
    void deveRecusarIniciarQuandoJaHaCronometroAtivoEmOutraTarefa() {
        // Given: o usuário tem um cronômetro rodando há 5 minutos na tarefa 99
        tarefa(TaskStatus.PENDING);
        sessaoAbertaDoUsuario(TaskSessionFactory.aberta(USER, OUTRA_TASK, antes(5), null));

        // When / Then
        assertThatThrownBy(() -> timerService.start(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("99");
                });
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void devePermitirIniciarQuandoOCronometroAnteriorJaAcabouSozinhoFechandoONoLimite() {
        // Given: sessão de 15 min aberta há 20 min na tarefa 99 (venceu há 5 min e ninguém pausou)
        tarefa(TaskStatus.PENDING);
        TaskSession vencida = TaskSessionFactory.aberta(USER, OUTRA_TASK, antes(20), 15);
        sessaoVencidaDoUsuario(vencida);

        // When
        TimerResponse resposta = timerService.start(USER, TASK, 30);

        // Then: a vencida foi fechada no limite (e não "agora") e a nova abriu
        assertThat(vencida.getEndedAt()).isEqualTo(antes(5));
        verify(sessionRepository, times(2)).saveAndFlush(any());
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
    }

    @Test
    void deveRetornarConflitoQuandoOBancoRecusaASegundaSessaoAberta() {
        // Given: duas requisições simultâneas; o índice único parcial barra a segunda
        Task task = tarefa(TaskStatus.PENDING);
        when(sessionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        // When / Then
        assertThatThrownBy(() -> timerService.start(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(taskRepository, never()).save(any());
    }

    // ---------- pause ----------

    @Test
    void devePausarFechandoASessaoEContandoOTempo() {
        // Given: cronômetro livre rodando há 10 minutos
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession aberta = TaskSessionFactory.aberta(USER, TASK, antes(10), null);
        sessoesDaTarefa(aberta);

        // When
        TimerResponse resposta = timerService.pause(USER, TASK);

        // Then
        assertThat(aberta.getEndedAt()).isEqualTo(AGORA);
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.totalSeconds()).isEqualTo(600);
        assertThat(resposta.activeSession()).isNull();
    }

    @Test
    void devePausarNoLimiteQuandoOTempoEscolhidoJaTinhaAcabado() {
        // Given: 15 minutos escolhidos, aberta há 20: o usuário só "pausa" 5 minutos depois do fim
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession vencida = TaskSessionFactory.aberta(USER, TASK, antes(20), 15);
        sessaoAbertaDoUsuario(vencida);
        sessoesDaTarefa(vencida);

        // When
        TimerResponse resposta = timerService.pause(USER, TASK);

        // Then: contam 15 minutos, não 20, e a resposta é 200 (a sessão já tinha parado sozinha)
        assertThat(vencida.getEndedAt()).isEqualTo(antes(5));
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
    }

    @Test
    void devePausarSemMudarNadaQuandoATarefaJaEstaPausada() {
        // Given: em andamento, mas sem sessão aberta
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechada(USER, TASK, antes(30), antes(20)));

        // When
        TimerResponse resposta = timerService.pause(USER, TASK);

        // Then
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.totalSeconds()).isEqualTo(600);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarPausarTarefaNaoIniciada() {
        tarefa(TaskStatus.PENDING);

        assertThatThrownBy(() -> timerService.pause(USER, TASK))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("não foi iniciada");
                });
    }

    @Test
    void deveRecusarPausarTarefaConcluida() {
        tarefa(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> timerService.pause(USER, TASK))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deveLancarNaoEncontradoAoPausarTarefaDeOutroUsuario() {
        when(taskRepository.findByIdAndUserId(TASK, OUTRO_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timerService.pause(OUTRO_USER, TASK))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- resume ----------

    @Test
    void deveRetomarAbrindoUmaNovaSessaoSemApagarAAnterior() {
        // Given: tarefa pausada com uma sessão de 10 minutos
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechada(USER, TASK, antes(30), antes(20)));

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then
        verify(sessionRepository).saveAndFlush(any(TaskSession.class));
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.sessions()).hasSize(2);
        assertThat(resposta.totalSeconds()).isEqualTo(600);
    }

    @Test
    void deveRetomarComUmaNovaDuracaoEscolhida() {
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechada(USER, TASK, antes(30), antes(20)));

        TimerResponse resposta = timerService.resume(USER, TASK, 45);

        assertThat(resposta.activeSession().plannedMinutes()).isEqualTo(45);
        assertThat(resposta.activeSession().remainingSeconds()).isEqualTo(45 * 60L);
    }

    @Test
    void deveExigirNovaDuracaoParaRetomarDepoisQueOTempoEscolhidoAcabouSozinho() {
        // Given: a sessão de 15 min desta tarefa venceu (nada restou) e continua "aberta" no banco
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession vencida = TaskSessionFactory.aberta(USER, TASK, antes(20), 15);
        sessaoVencidaDoUsuario(vencida);
        sessoesDaTarefa(vencida);

        // When / Then: sem duração não há o que continuar
        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("durationMinutes");
                });
        // Só o fechamento da vencida foi gravado; nenhuma sessão nova
        verify(sessionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void devePermitirRetomarDepoisQueOTempoAcabouSozinhoInformandoUmaNovaDuracao() {
        // Given
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession vencida = TaskSessionFactory.aberta(USER, TASK, antes(20), 15);
        sessaoVencidaDoUsuario(vencida);
        sessoesDaTarefa(vencida);

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, 30);

        // Then: a vencida foi fechada no limite, a nova abriu com o bloco novo e o total é só o que foi contado
        assertThat(vencida.getEndedAt()).isEqualTo(antes(5));
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.sessions()).hasSize(2);
        assertThat(resposta.activeSession().plannedMinutes()).isEqualTo(30);
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
    }

    // ---------- resume: a pausa preserva o tempo restante ----------

    @Test
    void deveRetomarContinuandoOTempoRestanteDoBloco() {
        // Given: timer de 30 min pausado depois de 10 usados
        tarefa(TaskStatus.IN_PROGRESS);
        UUID bloco = UUID.randomUUID();
        sessoesDaTarefa(TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(60), antes(50), 30, bloco));

        // When: retoma sem informar duração
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then: continua o mesmo bloco, com os 20 minutos que sobraram como limite
        ArgumentCaptor<TaskSession> salva = ArgumentCaptor.forClass(TaskSession.class);
        verify(sessionRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getBlockId()).isEqualTo(bloco);
        assertThat(salva.getValue().getPlannedMinutes()).isEqualTo(30);
        assertThat(salva.getValue().getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(20)));
        assertThat(resposta.remainingSeconds()).isEqualTo(20 * 60);
        assertThat(resposta.activeSession().remainingSeconds()).isEqualTo(20 * 60);
    }

    @Test
    void deveSomarVariasPausasNoMesmoBlocoAoRetomar() {
        // Given: timer de 15 min pausado duas vezes (5 + 5 min usados)
        tarefa(TaskStatus.IN_PROGRESS);
        UUID bloco = UUID.randomUUID();
        sessoesDaTarefa(
                TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(60), antes(55), 15, bloco),
                TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(40), antes(35), 15, bloco));

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then: sobraram 5 minutos
        assertThat(resposta.remainingSeconds()).isEqualTo(5 * 60);
        assertThat(resposta.totalSeconds()).isEqualTo(10 * 60);
    }

    @Test
    void deveComecarUmBlocoNovoEDescartarORestanteQuandoOResumeInformaDuracao() {
        // Given: timer de 30 min pausado com 20 restantes
        tarefa(TaskStatus.IN_PROGRESS);
        UUID blocoAntigo = UUID.randomUUID();
        sessoesDaTarefa(TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(60), antes(50), 30, blocoAntigo));

        // When: retoma escolhendo 15 minutos
        TimerResponse resposta = timerService.resume(USER, TASK, 15);

        // Then: bloco novo com os 15 minutos inteiros; os 20 que sobravam não contam mais
        ArgumentCaptor<TaskSession> salva = ArgumentCaptor.forClass(TaskSession.class);
        verify(sessionRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getBlockId()).isNotNull().isNotEqualTo(blocoAntigo);
        assertThat(salva.getValue().getPlannedMinutes()).isEqualTo(15);
        assertThat(resposta.remainingSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void deveRecusarRetomarSemDuracaoQuandoOBlocoFoiTodoUsado() {
        // Given: timer de 15 min usado por inteiro (15 min) e pausado
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(30), antes(15), 15, UUID.randomUUID()));

        // When / Then
        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("durationMinutes");
                });
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveContinuarLivreQuandoASessaoAnteriorEraDeCronometroLivre() {
        // Given
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechada(USER, TASK, antes(30), antes(20)));

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then: segue sem limite e sem bloco
        ArgumentCaptor<TaskSession> salva = ArgumentCaptor.forClass(TaskSession.class);
        verify(sessionRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getPlannedMinutes()).isNull();
        assertThat(salva.getValue().getBlockId()).isNull();
        assertThat(resposta.remainingSeconds()).isNull();
    }

    @Test
    void deveRetomarLivreQuandoATarefaEmAndamentoNaoTemSessoes() {
        // Given: em andamento (status mudado por fora do cronômetro) e sem nenhuma sessão
        tarefa(TaskStatus.IN_PROGRESS);

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.activeSession().plannedMinutes()).isNull();
    }

    @Test
    void deveCalcularORestanteSoComOBlocoMaisRecente() {
        // Given: bloco antigo todo usado e bloco novo com 10 de 30 usados
        tarefa(TaskStatus.IN_PROGRESS);
        UUID antigo = UUID.randomUUID();
        UUID novo = UUID.randomUUID();
        sessoesDaTarefa(
                TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(120), antes(105), 15, antigo),
                TaskSessionFactory.fechadaDeBloco(USER, TASK, antes(60), antes(50), 30, novo));

        // When
        TimerResponse resposta = timerService.resume(USER, TASK, null);

        // Then
        assertThat(resposta.remainingSeconds()).isEqualTo(20 * 60);
    }

    @Test
    void devePreservarORestanteNaRespostaDaPausa() {
        // Given: timer de 15 min rodando há 5
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.aberta(USER, TASK, antes(5), 15));

        // When
        TimerResponse resposta = timerService.pause(USER, TASK);

        // Then: pausado, com os 10 minutos que faltam guardados
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.remainingSeconds()).isEqualTo(10 * 60);
    }

    @Test
    void deveInformarORestanteAoIniciarTimerENaoInformarNoCronometroLivre() {
        tarefa(TaskStatus.PENDING);
        assertThat(timerService.start(USER, TASK, 30).remainingSeconds()).isEqualTo(30 * 60);
    }

    @Test
    void naoDeveInformarRestanteAoIniciarCronometroLivre() {
        tarefa(TaskStatus.PENDING);
        assertThat(timerService.start(USER, TASK, null).remainingSeconds()).isNull();
    }

    @Test
    void deveRecusarRetomarQuandoOCronometroDaTarefaJaEstaRodando() {
        // Given
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession rodando = TaskSessionFactory.aberta(USER, TASK, antes(5), null);
        sessaoAbertaDoUsuario(rodando);
        sessoesDaTarefa(rodando);

        // When / Then
        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("já está em andamento");
                });
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarRetomarTarefaNaoIniciadaIndicandoOStart() {
        tarefa(TaskStatus.PENDING);

        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("start");
                });
    }

    @Test
    void deveRecusarRetomarTarefaConcluida() {
        tarefa(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deveRecusarRetomarQuandoOutraTarefaTemCronometroAtivo() {
        // Given: esta tarefa está pausada, mas o usuário já cronometra a 99
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(TaskSessionFactory.fechada(USER, TASK, antes(30), antes(20)));
        sessaoAbertaDoUsuario(TaskSessionFactory.aberta(USER, OUTRA_TASK, antes(2), 30));

        // When / Then
        assertThatThrownBy(() -> timerService.resume(USER, TASK, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("99");
                });
    }

    // ---------- finish ----------

    @Test
    void deveFinalizarFechandoASessaoEConcluindoATarefa() {
        // Given: cronômetro livre rodando há 30 minutos
        Task task = tarefa(TaskStatus.IN_PROGRESS);
        TaskSession aberta = TaskSessionFactory.aberta(USER, TASK, antes(30), null);
        sessoesDaTarefa(aberta);

        // When
        TimerResponse resposta = timerService.finish(USER, TASK);

        // Then
        assertThat(aberta.getEndedAt()).isEqualTo(AGORA);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(AGORA);
        verify(taskRepository).save(task);
        assertThat(resposta.state()).isEqualTo(TimerState.COMPLETED);
        assertThat(resposta.totalSeconds()).isEqualTo(30 * 60);
        // Concluída: não há timer em curso, então não há tempo restante
        assertThat(resposta.remainingSeconds()).isNull();
    }

    @Test
    void deveFinalizarUmaTarefaPausadaSemSessaoAberta() {
        // Given: duas sessões já fechadas (10 + 5 min)
        Task task = tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(
                TaskSessionFactory.fechada(USER, TASK, antes(60), antes(50)),
                TaskSessionFactory.fechada(USER, TASK, antes(40), antes(35)));

        // When
        TimerResponse resposta = timerService.finish(USER, TASK);

        // Then
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void deveContarSoOLimiteAoFinalizarDepoisDoFimDoTempoEscolhido() {
        // Given: 15 minutos escolhidos, finalizada 20 minutos depois
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession vencida = TaskSessionFactory.aberta(USER, TASK, antes(20), 15);
        sessaoAbertaDoUsuario(vencida);
        sessoesDaTarefa(vencida);

        // When
        TimerResponse resposta = timerService.finish(USER, TASK);

        // Then: o tempo investido é 15 minutos, não 20
        assertThat(resposta.totalSeconds()).isEqualTo(15 * 60);
        assertThat(resposta.state()).isEqualTo(TimerState.COMPLETED);
    }

    @Test
    void deveRecusarFinalizarTarefaNaoIniciada() {
        tarefa(TaskStatus.PENDING);

        assertThatThrownBy(() -> timerService.finish(USER, TASK))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deveRecusarFinalizarTarefaJaConcluida() {
        tarefa(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> timerService.finish(USER, TASK))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------- get ----------

    @Test
    void deveSomarTodasAsSessoesNaConsultaSemGravarNada() {
        // Given: 10 min + 5 min fechadas e uma livre rodando há 2 min
        tarefa(TaskStatus.IN_PROGRESS);
        sessoesDaTarefa(
                TaskSessionFactory.fechada(USER, TASK, antes(60), antes(50)),
                TaskSessionFactory.fechada(USER, TASK, antes(40), antes(35)),
                TaskSessionFactory.aberta(USER, TASK, antes(2), null));

        // When
        TimerResponse resposta = timerService.get(USER, TASK);

        // Then
        assertThat(resposta.state()).isEqualTo(TimerState.RUNNING);
        assertThat(resposta.totalSeconds()).isEqualTo(17 * 60);
        verify(sessionRepository, never()).saveAndFlush(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deveContarSoAteOLimiteNaConsultaQuandoASessaoVencidaAindaConstaAberta() {
        // Given: 30 min escolhidos, aberta há 50 e ainda não fechada no banco
        tarefa(TaskStatus.IN_PROGRESS);
        TaskSession vencida = TaskSessionFactory.aberta(USER, TASK, antes(50), 30);
        sessoesDaTarefa(vencida);

        // When
        TimerResponse resposta = timerService.get(USER, TASK);

        // Then: aparece como pausada, com 30 minutos, e a consulta não altera a sessão
        assertThat(resposta.state()).isEqualTo(TimerState.PAUSED);
        assertThat(resposta.totalSeconds()).isEqualTo(30 * 60);
        assertThat(resposta.activeSession()).isNull();
        assertThat(vencida.getEndedAt()).isNull();
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRetornarIdleParaTarefaQueNuncaFoiCronometrada() {
        tarefa(TaskStatus.PENDING);

        TimerResponse resposta = timerService.get(USER, TASK);

        assertThat(resposta.state()).isEqualTo(TimerState.IDLE);
        assertThat(resposta.totalSeconds()).isZero();
        assertThat(resposta.sessions()).isEmpty();
    }

    @Test
    void deveLancarNaoEncontradoAoConsultarTarefaDeOutroUsuario() {
        when(taskRepository.findByIdAndUserId(TASK, OUTRO_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timerService.get(OUTRO_USER, TASK)).isInstanceOf(ResourceNotFoundException.class);
    }
}
