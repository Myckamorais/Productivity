package com.myck.productivity.goal.service;

import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.common.Texts;
import com.myck.productivity.goal.dto.GoalProgress;
import com.myck.productivity.goal.dto.GoalRequest;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.Goal;
import com.myck.productivity.goal.entity.GoalStatus;
import com.myck.productivity.goal.mapper.GoalMapper;
import com.myck.productivity.goal.repository.GoalRepository;
import com.myck.productivity.task.repository.GoalTaskCount;
import com.myck.productivity.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;
    private final GoalMapper goalMapper;

    public GoalService(GoalRepository goalRepository, TaskRepository taskRepository, GoalMapper goalMapper) {
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.goalMapper = goalMapper;
    }

    /** Cria a meta do usuário; sem status informado, ela nasce IN_PROGRESS. Meta nova não tem tarefas, então o progresso é 0. */
    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        GoalStatus status = request.status() != null ? request.status() : GoalStatus.IN_PROGRESS;
        Goal goal = new Goal(userId, request.title().trim(), Texts.blankToNull(request.description()),
                request.deadline(), status);
        return goalMapper.toResponse(goalRepository.save(goal), GoalProgress.EMPTY);
    }

    /** Lista só as metas do usuário, das mais recentes para as mais antigas, cada uma com o progresso. */
    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        List<Goal> goals = goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, GoalProgress> progress = progressByGoal(userId, goals.stream().map(Goal::getId).toList());
        return goals.stream()
                .map(goal -> goalMapper.toResponse(goal, progress.getOrDefault(goal.getId(), GoalProgress.EMPTY)))
                .toList();
    }

    /** Busca uma meta do usuário com o progresso; se não existir ou for de outro usuário, a resposta é a mesma (404). */
    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long id) {
        Goal goal = findOwned(userId, id);
        return goalMapper.toResponse(goal, progressByGoal(userId, List.of(id)).getOrDefault(id, GoalProgress.EMPTY));
    }

    /** Edita a meta; o status só muda se vier no corpo, caso contrário o atual é mantido. */
    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest request) {
        Goal goal = findOwned(userId, id);
        goal.setTitle(request.title().trim());
        goal.setDescription(Texts.blankToNull(request.description()));
        goal.setDeadline(request.deadline());
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        goalRepository.save(goal);
        return goalMapper.toResponse(goal, progressByGoal(userId, List.of(id)).getOrDefault(id, GoalProgress.EMPTY));
    }

    /** Remove a meta do usuário. As tarefas dela não são apagadas: o banco apenas as desvincula (goal_id vira nulo). */
    @Transactional
    public void delete(Long userId, Long id) {
        goalRepository.delete(findOwned(userId, id));
    }

    // A busca já filtra pelo dono: meta de outro usuário é tratada como inexistente
    private Goal findOwned(Long userId, Long id) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada."));
    }

    // Progresso de várias metas em uma única consulta agrupada; metas sem tarefas ficam fora do mapa
    private Map<Long, GoalProgress> progressByGoal(Long userId, Collection<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return taskRepository.countByGoals(userId, goalIds).stream()
                .collect(Collectors.toMap(GoalTaskCount::goalId,
                        count -> new GoalProgress(count.total(), count.completed())));
    }
}
