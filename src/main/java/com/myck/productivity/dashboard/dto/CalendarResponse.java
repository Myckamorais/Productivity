package com.myck.productivity.dashboard.dto;

import com.myck.productivity.task.dto.TaskResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarefas por dia (pela data planejada) num intervalo.
 *
 * @param days um item por dia do intervalo, inclusive os sem tarefas (lista vazia), para o front montar a grade
 */
public record CalendarResponse(LocalDate from, LocalDate to, List<CalendarDay> days) {

    /** Um dia do calendário com as tarefas planejadas para ele, da maior para a menor prioridade. */
    public record CalendarDay(LocalDate date, List<TaskResponse> tasks) {
    }
}
