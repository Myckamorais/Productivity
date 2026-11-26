package com.myck.productivity.dashboard.mapper;

import com.myck.productivity.dashboard.dto.CalendarResponse;
import com.myck.productivity.dashboard.dto.CategoryStat;
import com.myck.productivity.dashboard.dto.DashboardTodayResponse;
import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportResponse;
import com.myck.productivity.dashboard.repository.DashboardQueries.PlannedCount;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.task.dto.TaskResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Monta as respostas do dashboard, do relatório e do calendário a partir dos números já agregados no banco. */
@Component
public class DashboardMapper {

    /** Resumo do dia: rendimento por tarefas planejadas e por foco em relação à meta diária. */
    public DashboardTodayResponse toToday(LocalDate today, ZoneId zone, long tasksCompleted, PlannedCount planned,
            long focusSeconds, Integer focusGoalMinutes, List<GoalResponse> goalsInProgress) {
        Integer focusPercent = focusGoalMinutes == null ? null : percent(focusSeconds, focusGoalMinutes * 60L);
        return new DashboardTodayResponse(today, zone.getId(), tasksCompleted, planned.planned(),
                planned.completed(), percent(planned.completed(), planned.planned()), focusSeconds,
                focusGoalMinutes, focusPercent, goalsInProgress);
    }

    /**
     * Relatório: completa os dias sem atividade com zero (o banco só devolve dias com conclusões), soma os
     * totais e calcula a média de foco só sobre os dias que já começaram.
     */
    public ReportResponse toReport(ReportPeriod period, ReportPeriod.DateRange range, ZoneId zone, LocalDate today,
            Map<LocalDate, Long> focusByDay, Map<LocalDate, Long> completedByDay, List<CategoryStat> categories) {
        List<ReportResponse.DailyStat> daily = new ArrayList<>();
        long totalCompleted = 0;
        long totalFocus = 0;
        long activeDays = 0;
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            long completed = completedByDay.getOrDefault(day, 0L);
            long focus = focusByDay.getOrDefault(day, 0L);
            daily.add(new ReportResponse.DailyStat(day, completed, focus));
            totalCompleted += completed;
            totalFocus += focus;
            if (completed > 0 || focus > 0) {
                activeDays++;
            }
        }
        long elapsedDays = elapsedDays(range, today);
        long average = elapsedDays == 0 ? 0 : totalFocus / elapsedDays;
        var summary = new ReportResponse.Summary(totalCompleted, totalFocus, activeDays, elapsedDays, average);
        return new ReportResponse(period, range.from(), range.to(), zone.getId(), summary, daily, categories);
    }

    /** Calendário: um item por dia do intervalo, com as tarefas da maior para a menor prioridade (desempate pelo id). */
    public CalendarResponse toCalendar(LocalDate from, LocalDate to, List<TaskResponse> tasks) {
        Map<LocalDate, List<TaskResponse>> byDay = tasks.stream()
                .collect(Collectors.groupingBy(TaskResponse::plannedDate));
        Comparator<TaskResponse> order = Comparator
                .comparing((TaskResponse task) -> task.priority().ordinal()).reversed()
                .thenComparing(TaskResponse::id);
        List<CalendarResponse.CalendarDay> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<TaskResponse> ofDay = byDay.getOrDefault(day, List.of()).stream().sorted(order).toList();
            days.add(new CalendarResponse.CalendarDay(day, ofDay));
        }
        return new CalendarResponse(from, to, days);
    }

    /**
     * Percentual arredondado de 0 em diante (pode passar de 100). Nulo quando a base é zero: sem nada
     * planejado não existe rendimento, e mostrar 0% daria a ideia errada de dia ruim.
     */
    static Integer percent(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return (int) Math.round(part * 100.0 / whole);
    }

    // Dias do período até hoje (inclusive). Período futuro: 0; período passado: todos os dias
    static long elapsedDays(ReportPeriod.DateRange range, LocalDate today) {
        if (today.isBefore(range.from())) {
            return 0;
        }
        LocalDate lastElapsed = today.isAfter(range.to()) ? range.to() : today;
        return ChronoUnit.DAYS.between(range.from(), lastElapsed) + 1;
    }
}
