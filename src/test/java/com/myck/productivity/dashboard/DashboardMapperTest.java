package com.myck.productivity.dashboard;

import com.myck.productivity.dashboard.dto.CalendarResponse;
import com.myck.productivity.dashboard.dto.CategoryStat;
import com.myck.productivity.dashboard.dto.DashboardTodayResponse;
import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportResponse;
import com.myck.productivity.dashboard.mapper.DashboardMapper;
import com.myck.productivity.dashboard.repository.DashboardQueries.PlannedCount;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Montagem dos DTOs do dashboard, do relatório e do calendário a partir de números já agregados. */
class DashboardMapperTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);   // quarta-feira

    private final DashboardMapper mapper = new DashboardMapper();

    // ---------- hoje ----------

    @Test
    void deveCalcularOsDoisRendimentosDoDia() {
        // 3 de 4 planejadas concluídas; 2h de foco com meta de 4h
        DashboardTodayResponse resposta = mapper.toToday(HOJE, SAO_PAULO, 2, new PlannedCount(4, 3), 7200, 240,
                List.of());

        assertThat(resposta.completionPercent()).isEqualTo(75);
        assertThat(resposta.focusPercent()).isEqualTo(50);
        assertThat(resposta.tasksCompleted()).isEqualTo(2);
        assertThat(resposta.plannedTasks()).isEqualTo(4);
        assertThat(resposta.plannedTasksCompleted()).isEqualTo(3);
        assertThat(resposta.timeZone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void naoDeveCalcularRendimentoPorTarefasQuandoNadaFoiPlanejado() {
        // Sem planejamento, 0% daria a ideia errada de um dia ruim
        DashboardTodayResponse resposta = mapper.toToday(HOJE, SAO_PAULO, 1, new PlannedCount(0, 0), 0, 240,
                List.of());

        assertThat(resposta.completionPercent()).isNull();
    }

    @Test
    void naoDeveCalcularRendimentoPorFocoSemMetaDiaria() {
        DashboardTodayResponse resposta = mapper.toToday(HOJE, SAO_PAULO, 0, new PlannedCount(2, 1), 3600, null,
                List.of());

        assertThat(resposta.focusGoalMinutes()).isNull();
        assertThat(resposta.focusPercent()).isNull();
    }

    @Test
    void devePermitirRendimentoDeFocoAcimaDeCem() {
        // 5h de foco com meta de 4h = 125%
        DashboardTodayResponse resposta = mapper.toToday(HOJE, SAO_PAULO, 0, new PlannedCount(0, 0), 18000, 240,
                List.of());

        assertThat(resposta.focusPercent()).isEqualTo(125);
    }

    @Test
    void deveArredondarOsPercentuais() {
        // 1 de 3 = 33,3%; 2 de 3 = 66,7%
        assertThat(mapper.toToday(HOJE, SAO_PAULO, 0, new PlannedCount(3, 1), 0, null, List.of())
                .completionPercent()).isEqualTo(33);
        assertThat(mapper.toToday(HOJE, SAO_PAULO, 0, new PlannedCount(3, 2), 0, null, List.of())
                .completionPercent()).isEqualTo(67);
    }

    // ---------- relatório ----------

    private static final ReportPeriod.DateRange SEMANA = ReportPeriod.WEEK.rangeContaining(HOJE);   // 23 a 29/11

    @Test
    void deveIncluirTodosOsDiasDoPeriodoComZeroNosDiasSemAtividade() {
        ReportResponse resposta = mapper.toReport(ReportPeriod.WEEK, SEMANA, SAO_PAULO, HOJE,
                Map.of(LocalDate.of(2026, 11, 23), 3600L),
                Map.of(LocalDate.of(2026, 11, 25), 2L),
                List.of());

        assertThat(resposta.daily()).hasSize(7);
        assertThat(resposta.daily()).extracting(ReportResponse.DailyStat::date)
                .startsWith(LocalDate.of(2026, 11, 23)).endsWith(LocalDate.of(2026, 11, 29));
        assertThat(resposta.daily().get(0).focusSeconds()).isEqualTo(3600);
        assertThat(resposta.daily().get(1).focusSeconds()).isZero();
        assertThat(resposta.daily().get(1).tasksCompleted()).isZero();
        assertThat(resposta.daily().get(2).tasksCompleted()).isEqualTo(2);
    }

    @Test
    void deveSomarOsTotaisEContarOsDiasAtivos() {
        // Seg: 1h e 1 concluída; Qua: 30 min; Dom (futuro): nada
        ReportResponse resposta = mapper.toReport(ReportPeriod.WEEK, SEMANA, SAO_PAULO, HOJE,
                Map.of(LocalDate.of(2026, 11, 23), 3600L, LocalDate.of(2026, 11, 25), 1800L),
                Map.of(LocalDate.of(2026, 11, 23), 1L, LocalDate.of(2026, 11, 24), 2L),
                List.of());

        assertThat(resposta.summary().focusSeconds()).isEqualTo(5400);
        assertThat(resposta.summary().tasksCompleted()).isEqualTo(3);
        // Seg (foco e conclusão), Ter (só conclusão) e Qua (só foco)
        assertThat(resposta.summary().activeDays()).isEqualTo(3);
    }

    @Test
    void deveCalcularAMediaSoSobreOsDiasJaDecorridos() {
        // Semana atual: seg, ter e qua já começaram; qui a dom ainda não puxam a média para baixo
        ReportResponse resposta = mapper.toReport(ReportPeriod.WEEK, SEMANA, SAO_PAULO, HOJE,
                Map.of(LocalDate.of(2026, 11, 23), 5400L), Map.of(), List.of());

        assertThat(resposta.summary().elapsedDays()).isEqualTo(3);
        assertThat(resposta.summary().averageFocusSecondsPerDay()).isEqualTo(1800);
    }

    @Test
    void deveConsiderarTodosOsDiasDecorridosNumPeriodoPassadoENenhumNumFuturo() {
        var semanaPassada = ReportPeriod.WEEK.rangeContaining(HOJE.minusWeeks(1));
        var semanaQueVem = ReportPeriod.WEEK.rangeContaining(HOJE.plusWeeks(1));

        ReportResponse passada = mapper.toReport(ReportPeriod.WEEK, semanaPassada, SAO_PAULO, HOJE,
                Map.of(semanaPassada.from(), 7000L), Map.of(), List.of());
        ReportResponse futura = mapper.toReport(ReportPeriod.WEEK, semanaQueVem, SAO_PAULO, HOJE,
                Map.of(), Map.of(), List.of());

        assertThat(passada.summary().elapsedDays()).isEqualTo(7);
        assertThat(passada.summary().averageFocusSecondsPerDay()).isEqualTo(1000);
        assertThat(futura.summary().elapsedDays()).isZero();
        assertThat(futura.summary().averageFocusSecondsPerDay()).isZero();
    }

    @Test
    void deveManterAOrdemDasCategoriasVindaDoBancoEOsDadosDoPeriodo() {
        var categorias = List.of(
                new CategoryStat(2L, "Trabalho", "#EF4444", 3, 600),
                new CategoryStat(1L, "Estudos", "#3B82F6", 1, 7200));

        ReportResponse resposta = mapper.toReport(ReportPeriod.MONTH,
                ReportPeriod.MONTH.rangeContaining(HOJE), SAO_PAULO, HOJE, Map.of(), Map.of(), categorias);

        assertThat(resposta.categories()).extracting(CategoryStat::name).containsExactly("Trabalho", "Estudos");
        assertThat(resposta.period()).isEqualTo(ReportPeriod.MONTH);
        assertThat(resposta.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(resposta.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(resposta.daily()).hasSize(30);
        assertThat(resposta.timeZone()).isEqualTo("America/Sao_Paulo");
    }

    // ---------- calendário ----------

    private static TaskResponse tarefa(long id, LocalDate dia, TaskPriority prioridade) {
        return new TaskResponse(id, "Tarefa " + id, null, 1L, null, prioridade, TaskStatus.PENDING, dia, null, null,
                0, TimerState.IDLE);
    }

    @Test
    void deveIncluirTodosOsDiasDoIntervaloMesmoSemTarefas() {
        LocalDate de = LocalDate.of(2026, 11, 23);

        CalendarResponse resposta = mapper.toCalendar(de, de.plusDays(2), List.of(tarefa(1, de.plusDays(1),
                TaskPriority.LOW)));

        assertThat(resposta.days()).hasSize(3);
        assertThat(resposta.days().get(0).tasks()).isEmpty();
        assertThat(resposta.days().get(1).tasks()).extracting(TaskResponse::id).containsExactly(1L);
        assertThat(resposta.days().get(2).tasks()).isEmpty();
    }

    @Test
    void deveOrdenarAsTarefasDoDiaPelaPrioridadeEDepoisPeloId() {
        LocalDate dia = LocalDate.of(2026, 11, 25);

        CalendarResponse resposta = mapper.toCalendar(dia, dia, List.of(
                tarefa(5, dia, TaskPriority.LOW),
                tarefa(3, dia, TaskPriority.HIGH),
                tarefa(4, dia, TaskPriority.MEDIUM),
                tarefa(1, dia, TaskPriority.HIGH)));

        assertThat(resposta.days().getFirst().tasks()).extracting(TaskResponse::id).containsExactly(1L, 3L, 4L, 5L);
    }
}
