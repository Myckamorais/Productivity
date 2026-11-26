package com.myck.productivity.task;

import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.PageResponse;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.goal.repository.GoalRepository;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.mapper.TaskMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Long USER = 1L;
    private static final Long OUTRO_USER = 2L;
    private static final Instant AGORA = Instant.parse("2026-11-25T15:00:00Z");

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private GoalRepository goalRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock relogioFixo = Clock.fixed(AGORA, ZoneOffset.UTC);
        taskService = new TaskService(taskRepository, categoryRepository, goalRepository, new TaskMapper(),
                relogioFixo);
    }

    // ---------- create ----------

    @Test
    void deveCriarTarefaPendenteComPrioridadeMediaQuandoNaoInformadas() {
        // Given
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);

        // When
        TaskResponse resposta = taskService.create(USER, TaskFactory.requisicaoMinima("  Estudar  ", 10L));

        // Then
        ArgumentCaptor<Task> salva = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(salva.capture());
        assertThat(salva.getValue().getUserId()).isEqualTo(USER);
        assertThat(salva.getValue().getTitle()).isEqualTo("Estudar");
        assertThat(resposta.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(resposta.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(resposta.goalId()).isNull();
        assertThat(resposta.completedAt()).isNull();
        verifyNoInteractions(goalRepository);
    }

    @Test
    void deveCriarTarefaComMetaPrioridadeEDataInformadas() {
        // Given
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);
        when(goalRepository.existsByIdAndUserId(5L, USER)).thenReturn(true);
        var data = LocalDate.of(2026, 12, 1);

        // When
        TaskResponse resposta = taskService.create(USER,
                TaskFactory.requisicao("Revisar", 10L, 5L, TaskPriority.HIGH, null, data));

        // Then
        assertThat(resposta.goalId()).isEqualTo(5L);
        assertThat(resposta.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(resposta.plannedDate()).isEqualTo(data);
    }

    @Test
    void deveRegistrarOMomentoDaConclusaoQuandoATarefaJaNasceConcluida() {
        // Given
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);

        // When
        TaskResponse resposta = taskService.create(USER,
                TaskFactory.requisicao("Antiga", 10L, null, null, TaskStatus.COMPLETED, null));

        // Then
        assertThat(resposta.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(resposta.completedAt()).isEqualTo(AGORA);
    }

    @Test
    void deveRecusarCriacaoComCategoriaDeOutroUsuario() {
        // Given: a categoria 10 existe, mas não é deste usuário
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> taskService.create(USER, TaskFactory.requisicaoMinima("X", 10L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Categoria não encontrada.");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deveRecusarCriacaoComMetaDeOutroUsuario() {
        // Given
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);
        when(goalRepository.existsByIdAndUserId(5L, USER)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> taskService.create(USER,
                TaskFactory.requisicao("X", 10L, 5L, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Meta não encontrada.");
        verify(taskRepository, never()).save(any());
    }

    // ---------- list ----------

    @Test
    void deveListarComPaginaETamanhoInformadosEOrdemFixa() {
        // Given
        Page<Task> pagina = new PageImpl<>(List.of(TaskFactory.estudarSpring(7L, USER)), PageRequest.of(1, 5), 6);
        when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), any(Pageable.class)))
                .thenReturn(pagina);

        // When
        PageResponse<TaskResponse> resposta = taskService.list(USER, null, null, null, null, null, 1, 5);

        // Then
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(ArgumentMatchers.<Specification<Task>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().stream().map(o -> o.getProperty()).toList())
                .containsExactly("plannedDate", "id");
        assertThat(resposta.content()).extracting(TaskResponse::id).containsExactly(7L);
        assertThat(resposta.page()).isEqualTo(1);
        assertThat(resposta.totalElements()).isEqualTo(6);
        assertThat(resposta.totalPages()).isEqualTo(2);
    }

    @Test
    void deveRecusarPaginaNegativa() {
        assertThatThrownBy(() -> taskService.list(USER, null, null, null, null, null, -1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void deveRecusarTamanhoZero() {
        assertThatThrownBy(() -> taskService.list(USER, null, null, null, null, null, 0, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deveRecusarTamanhoAcimaDoMaximo() {
        assertThatThrownBy(() -> taskService.list(USER, null, null, null, null, null, 0, 101))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("100"));
    }

    @Test
    void deveRecusarIntervaloComDataInicialDepoisDaFinal() {
        assertThatThrownBy(() -> taskService.list(USER, null, null, null,
                LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1), 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deveAceitarIntervaloComDataInicialIgualAFinal() {
        // Given
        when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        var dia = LocalDate.of(2026, 12, 1);

        // When / Then: um único dia é um intervalo válido
        assertThat(taskService.list(USER, null, null, null, dia, dia, 0, 20).content()).isEmpty();
    }

    // ---------- get ----------

    @Test
    void deveRetornarATarefaQuandoEDoUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(TaskFactory.estudarSpring(7L, USER)));

        // When / Then
        assertThat(taskService.get(USER, 7L).title()).isEqualTo("Estudar Spring");
    }

    @Test
    void deveLancarNaoEncontradoAoBuscarTarefaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.get(OUTRO_USER, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void deveSubstituirOsCamposEMudarPrioridadeEStatusQuandoInformados() {
        // Given
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));
        when(categoryRepository.existsByIdAndUserId(11L, USER)).thenReturn(true);
        when(goalRepository.existsByIdAndUserId(5L, USER)).thenReturn(true);

        // When
        TaskResponse resposta = taskService.update(USER, 7L, TaskFactory.requisicao("Novo", 11L, 5L,
                TaskPriority.HIGH, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 12, 5)));

        // Then
        assertThat(resposta.title()).isEqualTo("Novo");
        assertThat(resposta.categoryId()).isEqualTo(11L);
        assertThat(resposta.goalId()).isEqualTo(5L);
        assertThat(resposta.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(resposta.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(resposta.plannedDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        verify(taskRepository).save(tarefa);
    }

    @Test
    void deveManterPrioridadeEStatusEDesvincularMetaEDataQuandoOmitidos() {
        // Given: tarefa com meta, prioridade ALTA e concluída
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        tarefa.setGoalId(5L);
        tarefa.setPriority(TaskPriority.HIGH);
        tarefa.changeStatus(TaskStatus.COMPLETED, AGORA);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);

        // When: edição sem meta, data, prioridade nem status
        TaskResponse resposta = taskService.update(USER, 7L, TaskFactory.requisicaoMinima("Só o título", 10L));

        // Then
        assertThat(resposta.goalId()).isNull();
        assertThat(resposta.plannedDate()).isNull();
        assertThat(resposta.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(resposta.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void deveApagarOMomentoDaConclusaoAoReabrirATarefa() {
        // Given
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        tarefa.changeStatus(TaskStatus.COMPLETED, AGORA);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);

        // When
        TaskResponse resposta = taskService.update(USER, 7L,
                TaskFactory.requisicao("Reaberta", 10L, null, null, TaskStatus.PENDING, null));

        // Then
        assertThat(resposta.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(resposta.completedAt()).isNull();
    }

    @Test
    void deveLancarNaoEncontradoAoAtualizarTarefaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.update(OUTRO_USER, 7L, TaskFactory.requisicaoMinima("X", 10L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deveRecusarAtualizacaoParaCategoriaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(TaskFactory.estudarSpring(7L, USER)));
        when(categoryRepository.existsByIdAndUserId(99L, USER)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> taskService.update(USER, 7L, TaskFactory.requisicaoMinima("X", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deveRecusarAtualizacaoParaMetaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(TaskFactory.estudarSpring(7L, USER)));
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);
        when(goalRepository.existsByIdAndUserId(99L, USER)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> taskService.update(USER, 7L,
                TaskFactory.requisicao("X", 10L, 99L, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Meta não encontrada.");
    }

    // ---------- complete ----------

    @Test
    void deveConcluirATarefaRegistrandoOMomento() {
        // Given
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));

        // When
        TaskResponse resposta = taskService.complete(USER, 7L);

        // Then
        assertThat(resposta.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(resposta.completedAt()).isEqualTo(AGORA);
        verify(taskRepository).save(tarefa);
    }

    @Test
    void deveManterOMomentoOriginalQuandoATarefaJaEstavaConcluida() {
        // Given: concluída há 1 dia
        Instant ontem = AGORA.minusSeconds(86_400);
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        tarefa.changeStatus(TaskStatus.COMPLETED, ontem);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));

        // When
        TaskResponse resposta = taskService.complete(USER, 7L);

        // Then: concluir de novo não "rejuvenesce" a conclusão
        assertThat(resposta.completedAt()).isEqualTo(ontem);
    }

    @Test
    void deveLancarNaoEncontradoAoConcluirTarefaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.complete(OUTRO_USER, 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void deveRemoverATarefaDoUsuario() {
        // Given
        Task tarefa = TaskFactory.estudarSpring(7L, USER);
        when(taskRepository.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(tarefa));

        // When
        taskService.delete(USER, 7L);

        // Then
        verify(taskRepository).delete(tarefa);
    }

    @Test
    void naoDeveRemoverTarefaDeOutroUsuario() {
        // Given
        when(taskRepository.findByIdAndUserId(7L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> taskService.delete(OUTRO_USER, 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }
}
