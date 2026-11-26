package com.myck.productivity.task.service;

import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.PageResponse;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.common.Texts;
import com.myck.productivity.goal.repository.GoalRepository;
import com.myck.productivity.task.dto.TaskRequest;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.mapper.TaskMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskSessionRepository;
import com.myck.productivity.task.repository.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class TaskService {

    static final int MAX_PAGE_SIZE = 100;

    // Ordem fixa (não vem do cliente, para não aceitar campo inexistente): data planejada mais próxima primeiro,
    // tarefas sem data por último e id para o desempate ser estável entre páginas
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("plannedDate").nullsLast(),
            Sort.Order.asc("id"));

    private final TaskRepository taskRepository;
    private final TaskSessionRepository sessionRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final TaskMapper taskMapper;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, TaskSessionRepository sessionRepository,
            CategoryRepository categoryRepository, GoalRepository goalRepository, TaskMapper taskMapper,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.goalRepository = goalRepository;
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    /**
     * Cria a tarefa do usuário. A categoria (e a meta, se informada) precisam ser do próprio usuário;
     * sem prioridade vira MEDIUM e sem status vira PENDING.
     */
    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        validateReferences(userId, request.categoryId(), request.goalId());
        Task task = new Task(userId, request.categoryId(), request.goalId(), request.title().trim(),
                Texts.blankToNull(request.description()),
                request.priority() != null ? request.priority() : TaskPriority.MEDIUM,
                request.plannedDate());
        if (request.status() != null) {
            task.changeStatus(request.status(), clock.instant());
        }
        taskRepository.save(task);
        return taskMapper.toResponse(task);
    }

    /**
     * Lista as tarefas do usuário com filtros opcionais e paginação. O intervalo de datas é inclusivo
     * e usa a data planejada; página começa em 0 e o tamanho vai de 1 a 100.
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, TaskStatus status, Long categoryId, Long goalId,
            LocalDate from, LocalDate to, int page, int size) {
        if (page < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O parâmetro page não pode ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "O parâmetro size deve estar entre 1 e " + MAX_PAGE_SIZE + ".");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A data inicial não pode ser depois da final.");
        }
        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        Page<Task> result = taskRepository.findAll(
                TaskSpecifications.filter(userId, status, categoryId, goalId, from, to), pageable);
        return PageResponse.from(result.map(taskMapper::toResponse));
    }

    /** Busca uma tarefa do usuário; se não existir ou for de outro usuário, a resposta é a mesma (404). */
    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long id) {
        return taskMapper.toResponse(findOwned(userId, id));
    }

    /**
     * Edita a tarefa. Título, descrição, categoria, meta e data são substituídos (meta e data omitidas
     * são limpas); prioridade e status só mudam se vierem no corpo.
     */
    @Transactional
    public TaskResponse update(Long userId, Long id, TaskRequest request) {
        Task task = findOwned(userId, id);
        validateReferences(userId, request.categoryId(), request.goalId());
        task.setTitle(request.title().trim());
        task.setDescription(Texts.blankToNull(request.description()));
        task.setCategoryId(request.categoryId());
        task.setGoalId(request.goalId());
        task.setPlannedDate(request.plannedDate());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.status() != null) {
            Instant now = clock.instant();
            // Concluir ou voltar para PENDING encerra o trabalho na tarefa: não pode sobrar cronômetro rodando
            if (request.status() == TaskStatus.COMPLETED || request.status() == TaskStatus.PENDING) {
                stopRunningTimer(id, now);
            }
            task.changeStatus(request.status(), now);
        }
        taskRepository.save(task);
        return taskMapper.toResponse(task);
    }

    /**
     * Marca a tarefa como concluída e registra o momento. Se já estava concluída, mantém o momento original.
     * Se o cronômetro estava rodando, a sessão é fechada junto, para o tempo investido ser contabilizado.
     */
    @Transactional
    public TaskResponse complete(Long userId, Long id) {
        Task task = findOwned(userId, id);
        Instant now = clock.instant();
        stopRunningTimer(id, now);
        task.changeStatus(TaskStatus.COMPLETED, now);
        taskRepository.save(task);
        return taskMapper.toResponse(task);
    }

    /** Remove a tarefa, desde que seja do usuário. */
    @Transactional
    public void delete(Long userId, Long id) {
        taskRepository.delete(findOwned(userId, id));
    }

    // Fecha a sessão aberta da tarefa, se houver (a entidade é gerenciada, então a alteração é gravada no commit)
    private void stopRunningTimer(Long taskId, Instant now) {
        sessionRepository.findByTaskIdAndEndedAtIsNull(taskId).ifPresent(session -> session.stop(now));
    }

    // A busca já filtra pelo dono: tarefa de outro usuário é tratada como inexistente
    private Task findOwned(Long userId, Long id) {
        return taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada."));
    }

    // Impede vincular a tarefa a categoria ou meta de outro usuário: para quem pede, elas "não existem" (404)
    private void validateReferences(Long userId, Long categoryId, Long goalId) {
        if (!categoryRepository.existsByIdAndUserId(categoryId, userId)) {
            throw new ResourceNotFoundException("Categoria não encontrada.");
        }
        if (goalId != null && !goalRepository.existsByIdAndUserId(goalId, userId)) {
            throw new ResourceNotFoundException("Meta não encontrada.");
        }
    }
}
