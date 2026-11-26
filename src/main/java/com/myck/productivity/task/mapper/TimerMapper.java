package com.myck.productivity.task.mapper;

import com.myck.productivity.task.dto.TimerResponse;
import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.entity.TimerBlocks;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Monta a resposta do cronômetro: soma as sessões, calcula o tempo restante e deduz o estado. */
@Component
public class TimerMapper {

    public TimerResponse toResponse(Task task, List<TaskSession> sessions, Instant now) {
        Duration total = Duration.ZERO;
        TimerResponse.ActiveSession active = null;
        List<TimerResponse.SessionItem> items = new ArrayList<>();

        for (TaskSession session : sessions) {
            // O tempo de cada sessão para no limite escolhido, mesmo que ela ainda conste como aberta no banco
            Duration duration = session.durationAt(now);
            total = total.plus(duration);
            items.add(new TimerResponse.SessionItem(session.getId(), session.getStartedAt(),
                    session.effectiveEnd(now), session.getPlannedMinutes(), duration.getSeconds()));
            if (session.isActiveAt(now)) {
                Long remaining = session.getExpiresAt() == null
                        ? null
                        : Duration.between(now, session.getExpiresAt()).getSeconds();
                active = new TimerResponse.ActiveSession(session.getId(), session.getStartedAt(),
                        session.getPlannedMinutes(), session.getExpiresAt(), duration.getSeconds(), remaining);
            }
        }
        TimerState state = stateOf(task, active);
        return new TimerResponse(task.getId(), task.getStatus(), state, total.getSeconds(),
                remainingOf(state, sessions, now), active, items);
    }

    // O restante só faz sentido com a tarefa rodando ou pausada: antes de iniciar ou depois de concluir não há timer em curso
    private static Long remainingOf(TimerState state, List<TaskSession> sessions, Instant now) {
        if (state != TimerState.RUNNING && state != TimerState.PAUSED) {
            return null;
        }
        return TimerBlocks.remaining(sessions, now).map(Duration::getSeconds).orElse(null);
    }

    // Concluída vence; senão, sessão rodando = RUNNING; tarefa iniciada sem sessão rodando = PAUSED
    private static TimerState stateOf(Task task, TimerResponse.ActiveSession active) {
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return TimerState.COMPLETED;
        }
        if (active != null) {
            return TimerState.RUNNING;
        }
        return task.getStatus() == TaskStatus.IN_PROGRESS ? TimerState.PAUSED : TimerState.IDLE;
    }
}
