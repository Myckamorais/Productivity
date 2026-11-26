package com.myck.productivity.goal;

import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.goal.dto.GoalRequest;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.Goal;
import com.myck.productivity.goal.entity.GoalStatus;
import com.myck.productivity.goal.mapper.GoalMapper;
import com.myck.productivity.goal.repository.GoalRepository;
import com.myck.productivity.goal.service.GoalService;
import com.myck.productivity.task.repository.GoalTaskCount;
import com.myck.productivity.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final Long USER = 1L;
    private static final Long OUTRO_USER = 2L;

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private TaskRepository taskRepository;

    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, taskRepository, new GoalMapper());
    }

    // ---------- progresso ----------

    @Test
    void deveCriarMetaComProgressoZeroSemConsultarTarefas() {
        // Given
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        GoalResponse resposta = goalService.create(USER, new GoalRequest("Meta", null, null, null));

        // Then
        assertThat(resposta.totalTasks()).isZero();
        assertThat(resposta.progressPercent()).isZero();
        verifyNoInteractions(taskRepository);
    }

    @Test
    void deveCalcularOProgressoDaMetaComAsTarefasConcluidas() {
        // Given: 3 tarefas, 1 concluída
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(GoalFactory.aprenderJava(5L, USER)));
        when(taskRepository.countByGoals(USER, List.of(5L))).thenReturn(List.of(new GoalTaskCount(5L, 3L, 1L)));

        // When
        GoalResponse resposta = goalService.get(USER, 5L);

        // Then
        assertThat(resposta.totalTasks()).isEqualTo(3);
        assertThat(resposta.completedTasks()).isEqualTo(1);
        assertThat(resposta.progressPercent()).isEqualTo(33);
    }

    @Test
    void deveCalcularOProgressoDeTodasAsMetasDaListagemEmUmaUnicaConsulta() {
        // Given: duas metas; só a 5 tem tarefas
        when(goalRepository.findAllByUserIdOrderByCreatedAtDesc(USER))
                .thenReturn(List.of(GoalFactory.aprenderJava(5L, USER), GoalFactory.aprenderJava(6L, USER)));
        when(taskRepository.countByGoals(USER, List.of(5L, 6L))).thenReturn(List.of(new GoalTaskCount(5L, 2L, 2L)));

        // When
        List<GoalResponse> lista = goalService.list(USER);

        // Then
        assertThat(lista).extracting(GoalResponse::progressPercent).containsExactly(100, 0);
        verify(taskRepository, times(1)).countByGoals(any(), any());
    }

    @Test
    void naoDeveConsultarTarefasQuandoOUsuarioNaoTemMetas() {
        // Given
        when(goalRepository.findAllByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());

        // When / Then
        assertThat(goalService.list(USER)).isEmpty();
        verifyNoInteractions(taskRepository);
    }

    @Test
    void deveRetornarOProgressoAtualDepoisDeEditarAMeta() {
        // Given
        Goal meta = GoalFactory.aprenderJava(5L, USER);
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(meta));
        when(goalRepository.save(meta)).thenReturn(meta);
        when(taskRepository.countByGoals(USER, List.of(5L))).thenReturn(List.of(new GoalTaskCount(5L, 4L, 3L)));

        // When
        GoalResponse resposta = goalService.update(USER, 5L, new GoalRequest("Novo título", null, null, null));

        // Then
        assertThat(resposta.progressPercent()).isEqualTo(75);
    }

    // ---------- create ----------

    @Test
    void deveCriarMetaEmAndamentoQuandoOStatusNaoEInformado() {
        // Given
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        GoalResponse resposta = goalService.create(USER,
                new GoalRequest("  Aprender Java ", "Curso completo", LocalDate.of(2026, 12, 31), null));

        // Then
        ArgumentCaptor<Goal> salva = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(salva.capture());
        assertThat(salva.getValue().getUserId()).isEqualTo(USER);
        assertThat(salva.getValue().getTitle()).isEqualTo("Aprender Java");
        assertThat(resposta.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(resposta.deadline()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void deveRespeitarOStatusInformadoNaCriacao() {
        // Given
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        GoalResponse resposta = goalService.create(USER,
                new GoalRequest("Meta antiga", null, null, GoalStatus.COMPLETED));

        // Then
        assertThat(resposta.status()).isEqualTo(GoalStatus.COMPLETED);
    }

    @Test
    void deveGuardarDescricaoEmBrancoComoNula() {
        // Given
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        GoalResponse resposta = goalService.create(USER, new GoalRequest("Meta", "   ", null, null));

        // Then
        assertThat(resposta.description()).isNull();
    }

    // ---------- list / get ----------

    @Test
    void deveListarAsMetasDoUsuario() {
        // Given
        when(goalRepository.findAllByUserIdOrderByCreatedAtDesc(USER))
                .thenReturn(List.of(GoalFactory.aprenderJava(5L, USER)));

        // When / Then
        assertThat(goalService.list(USER)).extracting(GoalResponse::id).containsExactly(5L);
    }

    @Test
    void deveRetornarAMetaQuandoEDoUsuario() {
        // Given
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(GoalFactory.aprenderJava(5L, USER)));

        // When / Then
        assertThat(goalService.get(USER, 5L).title()).isEqualTo("Aprender Java");
    }

    @Test
    void deveLancarNaoEncontradoAoBuscarMetaDeOutroUsuario() {
        // Given
        when(goalRepository.findByIdAndUserId(5L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> goalService.get(OUTRO_USER, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void deveAtualizarOsCamposEMudarOStatusQuandoInformado() {
        // Given
        Goal meta = GoalFactory.aprenderJava(5L, USER);
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(meta));
        when(goalRepository.save(meta)).thenReturn(meta);

        // When
        GoalResponse resposta = goalService.update(USER, 5L,
                new GoalRequest("Aprender Spring", "Fase 2", null, GoalStatus.COMPLETED));

        // Then
        assertThat(resposta.title()).isEqualTo("Aprender Spring");
        assertThat(resposta.description()).isEqualTo("Fase 2");
        assertThat(resposta.deadline()).isNull();
        assertThat(resposta.status()).isEqualTo(GoalStatus.COMPLETED);
    }

    @Test
    void deveManterOStatusAtualQuandoAEdicaoNaoInformaStatus() {
        // Given
        Goal meta = GoalFactory.aprenderJava(5L, USER);
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(meta));
        when(goalRepository.save(meta)).thenReturn(meta);

        // When
        GoalResponse resposta = goalService.update(USER, 5L, new GoalRequest("Novo título", null, null, null));

        // Then
        assertThat(resposta.status()).isEqualTo(GoalStatus.IN_PROGRESS);
    }

    @Test
    void deveLancarNaoEncontradoAoAtualizarMetaDeOutroUsuario() {
        // Given
        when(goalRepository.findByIdAndUserId(5L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> goalService.update(OUTRO_USER, 5L, new GoalRequest("X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(goalRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void deveRemoverAMetaDoUsuario() {
        // Given
        Goal meta = GoalFactory.aprenderJava(5L, USER);
        when(goalRepository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(meta));

        // When
        goalService.delete(USER, 5L);

        // Then
        verify(goalRepository).delete(meta);
    }

    @Test
    void naoDeveRemoverMetaDeOutroUsuario() {
        // Given
        when(goalRepository.findByIdAndUserId(5L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> goalService.delete(OUTRO_USER, 5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(goalRepository, never()).delete(any());
    }
}
