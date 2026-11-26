package com.myck.productivity.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de um período (semana, mês ou ano), no fuso do usuário.
 *
 * @param daily      um item por dia do período, inclusive os sem atividade e os futuros (com zero)
 * @param categories categorias com atividade no período, das que mais concluíram tarefas para as que menos
 */
public record ReportResponse(
        ReportPeriod period,
        LocalDate from,
        LocalDate to,
        String timeZone,
        Summary summary,
        List<DailyStat> daily,
        List<CategoryStat> categories) {

    /**
     * Totais do período.
     *
     * @param activeDays                dias com alguma tarefa concluída ou algum tempo de foco
     * @param elapsedDays               dias do período que já começaram (até hoje); num período passado, todos
     * @param averageFocusSecondsPerDay foco total ÷ dias já decorridos (dias futuros não puxam a média para baixo)
     */
    public record Summary(long tasksCompleted, long focusSeconds, long activeDays, long elapsedDays,
            long averageFocusSecondsPerDay) {
    }

    /** Produtividade de um dia. */
    public record DailyStat(LocalDate date, long tasksCompleted, long focusSeconds) {
    }
}
