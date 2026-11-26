package com.myck.productivity.task.service;

import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.task.dto.TimerResponse;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.entity.TimerBlocks;
import com.myck.productivity.task.mapper.TimerMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cronômetro das tarefas (opcional): start, pause, resume e finish. Cada start/resume abre uma sessão
 * e cada pause/finish a fecha; o tempo da tarefa é a soma das sessões. Em cada operação a hora vem de
 * uma única leitura do relógio, para os cálculos da mesma requisição serem coerentes.
 */
@Service
public class TaskTimerService {

    private final TaskRepository taskRepository;
    private final TaskSessionRepository sessionRepository;
    private final TimerMapper timerMapper;
    private final Clock clock;

    public TaskTimerService(TaskRepository taskRepository, TaskSessionRepository sessionRepository,
            TimerMapper timerMapper, Clock clock) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
        this.timerMapper = timerMapper;
        this.clock = clock;
    }

    /**
     * Inicia o cronômetro de uma tarefa ainda não iniciada e a põe em andamento. Sem duração, é cronômetro
     * livre; com 15, 30, 45 ou 60, para sozinho no limite. Só um cronômetro ativo por usuário (409).
     */
    @Transactional
    public TimerResponse start(Long userId, Long taskId, Integer plannedMinutes) {
        Instant now = clock.instant();
        Task task = findOwned(userId, taskId);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw conflict("A tarefa já está concluída.");
        }
        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            throw conflict("A tarefa já foi iniciada. Use resume para retomar o cronômetro.");
        }
        settleExpired(userId, now);
        ensureNoActiveTimer(userId);

        List<TaskSession> sessions = loadSessions(taskId);
        // Todo start abre um bloco novo: com duração, o tempo escolhido inteiro; sem duração, cronômetro livre
        openSession(new TaskSession(userId, taskId, now, plannedMinutes), sessions);
        task.changeStatus(TaskStatus.IN_PROGRESS, now);
        taskRepository.save(task);
        return timerMapper.toResponse(task, sessions, now);
    }

    /**
     * Pausa o cronômetro fechando a sessão aberta. Se a tarefa já está em andamento sem sessão rodando
     * (já pausada, ou o tempo escolhido acabou sozinho), não muda nada e responde 200: nos dois casos o
     * resultado que o usuário queria, "cronômetro parado", já aconteceu.
     */
    @Transactional
    public TimerResponse pause(Long userId, Long taskId) {
        Instant now = clock.instant();
        Task task = findOwned(userId, taskId);
        requireStarted(task);
        settleExpired(userId, now);

        List<TaskSession> sessions = loadSessions(taskId);
        stopOpenSession(sessions, now);
        return timerMapper.toResponse(task, sessions, now);
    }

    /**
     * Retoma o trabalho numa tarefa iniciada e pausada, abrindo uma nova sessão. A pausa preserva o que sobrou:
     * <ul>
     *   <li>sem duração, continua o que era: timer segue com o tempo restante do bloco e cronômetro livre segue livre;
     *       se o tempo escolhido já tinha acabado, recusa (409) e pede uma nova duração;</li>
     *   <li>com duração (15, 30, 45 ou 60), começa um bloco novo e o restante do anterior é descartado.</li>
     * </ul>
     * Também recusa se a tarefa já está rodando ou se há outro cronômetro ativo do usuário (409).
     */
    @Transactional
    public TimerResponse resume(Long userId, Long taskId, Integer plannedMinutes) {
        Instant now = clock.instant();
        Task task = findOwned(userId, taskId);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw conflict("A tarefa já está concluída.");
        }
        if (task.getStatus() == TaskStatus.PENDING) {
            throw conflict("A tarefa ainda não foi iniciada. Use start para iniciar o cronômetro.");
        }
        settleExpired(userId, now);

        List<TaskSession> sessions = loadSessions(taskId);
        if (sessions.stream().anyMatch(session -> session.getEndedAt() == null)) {
            throw conflict("O cronômetro desta tarefa já está em andamento.");
        }
        ensureNoActiveTimer(userId);
        TaskSession next = plannedMinutes != null
                ? new TaskSession(userId, taskId, now, plannedMinutes)
                : continueLastBlock(userId, taskId, now, sessions);
        openSession(next, sessions);
        return timerMapper.toResponse(task, sessions, now);
    }

    /** Finaliza: fecha a sessão aberta (se houver) e conclui a tarefa. Vale também para tarefa pausada. */
    @Transactional
    public TimerResponse finish(Long userId, Long taskId) {
        Instant now = clock.instant();
        Task task = findOwned(userId, taskId);
        requireStarted(task);
        settleExpired(userId, now);

        List<TaskSession> sessions = loadSessions(taskId);
        stopOpenSession(sessions, now);
        task.changeStatus(TaskStatus.COMPLETED, now);
        taskRepository.save(task);
        return timerMapper.toResponse(task, sessions, now);
    }

    /**
     * Consulta o cronômetro sem alterar nada: uma sessão cujo tempo escolhido já acabou aparece como
     * encerrada no limite, mesmo que o banco ainda a guarde como aberta.
     */
    @Transactional(readOnly = true)
    public TimerResponse get(Long userId, Long taskId) {
        Task task = findOwned(userId, taskId);
        return timerMapper.toResponse(task, loadSessions(taskId), clock.instant());
    }

    // A busca já filtra pelo dono: tarefa de outro usuário é tratada como inexistente
    private Task findOwned(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada."));
    }

    // Pausar e finalizar só fazem sentido para uma tarefa em andamento
    private static void requireStarted(Task task) {
        if (task.getStatus() == TaskStatus.PENDING) {
            throw conflict("A tarefa ainda não foi iniciada.");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw conflict("A tarefa já está concluída.");
        }
    }

    /**
     * Fecha, no limite certo, a sessão aberta do usuário cujo tempo escolhido já acabou. Sem isso ela
     * continuaria "aberta" no banco e bloquearia qualquer novo cronômetro do usuário. O flush é imediato
     * porque o Hibernate executa INSERTs antes de UPDATEs, e a sessão nova violaria o índice de sessão
     * única aberta se este fechamento ainda estivesse pendente.
     */
    private void settleExpired(Long userId, Instant now) {
        sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .filter(session -> !session.isActiveAt(now))
                .ifPresent(session -> {
                    session.stop(now);
                    sessionRepository.saveAndFlush(session);
                });
    }

    // Só um cronômetro ativo por usuário. Depois do settleExpired, qualquer sessão aberta está de fato rodando
    private void ensureNoActiveTimer(Long userId) {
        sessionRepository.findByUserIdAndEndedAtIsNull(userId).ifPresent(active -> {
            throw activeTimerConflict(active.getTaskId());
        });
    }

    /**
     * Sessão de continuação do que estava em andamento. Se a sessão mais recente era de timer, segue o mesmo
     * bloco com só o tempo restante como limite; se era livre (ou não há sessões), segue livre.
     * Bloco todo usado não tem como continuar: pede uma nova duração.
     */
    private TaskSession continueLastBlock(Long userId, Long taskId, Instant now, List<TaskSession> sessions) {
        Optional<Duration> remaining = TimerBlocks.remaining(sessions, now);
        if (remaining.isEmpty()) {
            return new TaskSession(userId, taskId, now, null);
        }
        if (remaining.get().isZero()) {
            throw conflict("O tempo escolhido acabou. Informe uma nova duração (durationMinutes) para retomar.");
        }
        TaskSession latest = sessions.getLast();
        return new TaskSession(userId, taskId, now, latest.getPlannedMinutes(), latest.getBlockId(), remaining.get());
    }

    // Abre a nova sessão. Se duas requisições simultâneas passarem pela checagem, o índice único do banco barra a segunda
    private void openSession(TaskSession session, List<TaskSession> sessions) {
        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw conflict("Já existe um cronômetro ativo. Pause-o antes de iniciar outro.");
        }
        sessions.add(session);
    }

    // Fecha a sessão ainda aberta da lista (a entidade é gerenciada, então a alteração é gravada no commit)
    private static void stopOpenSession(List<TaskSession> sessions, Instant now) {
        sessions.stream()
                .filter(session -> session.getEndedAt() == null)
                .findFirst()
                .ifPresent(session -> session.stop(now));
    }

    // Lista mutável: o start/resume acrescenta a sessão nova para a resposta já vir completa, sem reconsultar
    private List<TaskSession> loadSessions(Long taskId) {
        return new ArrayList<>(sessionRepository.findAllByTaskIdOrderByStartedAtAsc(taskId));
    }

    private static BusinessException activeTimerConflict(Long taskId) {
        return conflict("Já existe um cronômetro ativo na tarefa " + taskId + ". Pause-o antes de iniciar outro.");
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
