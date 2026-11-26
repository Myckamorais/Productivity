package com.myck.productivity.goal.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.goal.dto.GoalRequest;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /** Cria uma meta do usuário logado e retorna os dados criados (201). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody GoalRequest request) {
        return goalService.create(user.id(), request);
    }

    /** Lista as metas do usuário logado, das mais recentes para as mais antigas. */
    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return goalService.list(user.id());
    }

    /** Retorna uma meta do usuário logado; 404 se não existir ou for de outro usuário. */
    @GetMapping("/{id}")
    public GoalResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return goalService.get(user.id(), id);
    }

    /** Atualiza uma meta do usuário e retorna o resultado; 404 se não existir ou for de outro usuário. */
    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody GoalRequest request) {
        return goalService.update(user.id(), id, request);
    }

    /** Remove uma meta do usuário e não retorna corpo (204); 404 se não existir ou for de outro usuário. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        goalService.delete(user.id(), id);
    }
}
