package com.myck.productivity.task.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.task.dto.StartTimerRequest;
import com.myck.productivity.task.dto.TimerResponse;
import com.myck.productivity.task.service.TaskTimerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks/{id}")
public class TaskTimerController {

    private final TaskTimerService timerService;

    public TaskTimerController(TaskTimerService timerService) {
        this.timerService = timerService;
    }

    /**
     * Inicia o cronômetro da tarefa e retorna a situação dele. O corpo é opcional: sem duração é cronômetro
     * livre; com durationMinutes de 15, 30, 45 ou 60 para sozinho no limite. 409 se a tarefa já foi iniciada,
     * está concluída ou o usuário já tem outro cronômetro ativo.
     */
    @PostMapping("/start")
    public TimerResponse start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody(required = false) StartTimerRequest request) {
        return timerService.start(user.id(), id, minutesOf(request));
    }

    /** Pausa o cronômetro da tarefa e retorna a situação dele; 409 se a tarefa não foi iniciada ou já está concluída. */
    @PostMapping("/pause")
    public TimerResponse pause(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return timerService.pause(user.id(), id);
    }

    /**
     * Retoma a tarefa pausada numa nova sessão, preservando o tempo restante do timer. Sem corpo continua o que
     * era (timer com o que sobrou, ou cronômetro livre); com durationMinutes começa um bloco novo e descarta o
     * restante. 409 se já está rodando, se há outro cronômetro ativo ou se o tempo escolhido acabou e nenhuma nova duração veio.
     */
    @PostMapping("/resume")
    public TimerResponse resume(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody(required = false) StartTimerRequest request) {
        return timerService.resume(user.id(), id, minutesOf(request));
    }

    /** Finaliza: fecha a sessão aberta, conclui a tarefa e retorna o tempo total; 409 se não foi iniciada ou já concluída. */
    @PostMapping("/finish")
    public TimerResponse finish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return timerService.finish(user.id(), id);
    }

    /** Retorna a situação do cronômetro: estado, tempo total, sessão ativa (com tempo restante) e histórico de sessões. */
    @GetMapping("/timer")
    public TimerResponse timer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return timerService.get(user.id(), id);
    }

    // Corpo ausente = cronômetro livre
    private static Integer minutesOf(StartTimerRequest request) {
        return request == null ? null : request.durationMinutes();
    }
}
