package com.myck.productivity.task.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.common.PageResponse;
import com.myck.productivity.task.dto.TaskRequest;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Cria uma tarefa do usuário logado e retorna os dados criados (201); categoria ou meta de outro usuário retorna 404. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TaskRequest request) {
        return taskService.create(user.id(), request);
    }

    /**
     * Lista as tarefas do usuário logado, paginadas, com filtros opcionais por status, categoria, meta e
     * intervalo de datas planejadas (from e to, formato AAAA-MM-DD, inclusivos). Page começa em 0; size vai de 1 a 100.
     */
    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taskService.list(user.id(), status, categoryId, goalId, from, to, page, size);
    }

    /** Retorna uma tarefa do usuário logado; 404 se não existir ou for de outro usuário. */
    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return taskService.get(user.id(), id);
    }

    /** Atualiza uma tarefa do usuário e retorna o resultado; 404 se ela, a categoria ou a meta não forem do usuário. */
    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody TaskRequest request) {
        return taskService.update(user.id(), id, request);
    }

    /** Marca a tarefa como concluída e retorna o resultado com o momento da conclusão; 404 se não for do usuário. */
    @PatchMapping("/{id}/complete")
    public TaskResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return taskService.complete(user.id(), id);
    }

    /** Remove uma tarefa do usuário e não retorna corpo (204); 404 se não existir ou for de outro usuário. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        taskService.delete(user.id(), id);
    }
}
