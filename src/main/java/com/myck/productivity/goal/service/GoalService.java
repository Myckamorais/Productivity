package com.myck.productivity.goal.service;

import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.goal.dto.GoalRequest;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.Goal;
import com.myck.productivity.goal.entity.GoalStatus;
import com.myck.productivity.goal.mapper.GoalMapper;
import com.myck.productivity.goal.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalMapper goalMapper;

    public GoalService(GoalRepository goalRepository, GoalMapper goalMapper) {
        this.goalRepository = goalRepository;
        this.goalMapper = goalMapper;
    }

    /** Cria a meta do usuário; sem status informado, ela nasce IN_PROGRESS. */
    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        GoalStatus status = request.status() != null ? request.status() : GoalStatus.IN_PROGRESS;
        Goal goal = new Goal(userId, request.title().trim(), blankToNull(request.description()),
                request.deadline(), status);
        return goalMapper.toResponse(goalRepository.save(goal));
    }

    /** Lista só as metas do usuário, das mais recentes para as mais antigas. */
    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        return goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(goalMapper::toResponse)
                .toList();
    }

    /** Busca uma meta do usuário; se não existir ou for de outro usuário, a resposta é a mesma (404). */
    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long id) {
        return goalMapper.toResponse(findOwned(userId, id));
    }

    /** Edita a meta; o status só muda se vier no corpo, caso contrário o atual é mantido. */
    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest request) {
        Goal goal = findOwned(userId, id);
        goal.setTitle(request.title().trim());
        goal.setDescription(blankToNull(request.description()));
        goal.setDeadline(request.deadline());
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        return goalMapper.toResponse(goalRepository.save(goal));
    }

    /** Remove a meta, desde que seja do usuário. */
    @Transactional
    public void delete(Long userId, Long id) {
        goalRepository.delete(findOwned(userId, id));
    }

    // A busca já filtra pelo dono: meta de outro usuário é tratada como inexistente
    private Goal findOwned(Long userId, Long id) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada."));
    }

    // Descrição em branco vira nulo, para o banco não guardar texto vazio
    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
