package com.myck.productivity.dashboard.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.dashboard.dto.CalendarResponse;
import com.myck.productivity.dashboard.dto.DashboardTodayResponse;
import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportResponse;
import com.myck.productivity.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Retorna o resumo de hoje no fuso do usuário: concluídas, rendimento, tempo de foco e metas em andamento. */
    @GetMapping("/dashboard/today")
    public DashboardTodayResponse today(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.today(user.id());
    }

    /**
     * Retorna o relatório da semana (seg–dom), mês ou ano que contém {@code date} (AAAA-MM-DD; padrão hoje):
     * produtividade por dia, categorias que mais concluíram tarefas e resumo.
     */
    @GetMapping("/reports")
    public ReportResponse report(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam ReportPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dashboardService.report(user.id(), period, date);
    }

    /** Retorna as tarefas planejadas por dia entre from e to (AAAA-MM-DD, inclusivos, no máximo 92 dias). */
    @GetMapping("/calendar")
    public CalendarResponse calendar(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.calendar(user.id(), from, to);
    }
}
