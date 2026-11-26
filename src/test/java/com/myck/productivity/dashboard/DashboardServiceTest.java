package com.myck.productivity.dashboard;

import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.dashboard.dto.DashboardTodayResponse;
import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportResponse;
import com.myck.productivity.dashboard.mapper.DashboardMapper;
import com.myck.productivity.dashboard.repository.DashboardQueries;
import com.myck.productivity.dashboard.repository.DashboardQueries.PlannedCount;
import com.myck.productivity.dashboard.service.DashboardService;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.GoalStatus;
import com.myck.productivity.goal.service.GoalService;
import com.myck.productivity.task.mapper.TaskMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskTimeQueries;
import com.myck.productivity.user.UserFactory;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long USER = 1L;
    // 26/11 às 01:30 em UTC ainda é 25/11 às 22:30 em São Paulo
    private static final Instant AGORA = Instant.parse("2026-11-26T01:30:00Z");
    private static final LocalDate HOJE_SP = LocalDate.of(2026, 11, 25);
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Mock
    private UserRepository userRepository;
    @Mock
    private DashboardQueries queries;
    @Mock
    private GoalService goalService;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskTimeQueries taskTimeQueries;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(userRepository, queries, goalService, taskRepository, taskTimeQueries,
                new TaskMapper(), new DashboardMapper(), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private User usuario(String fuso, Integer metaMinutos) {
        User user = UserFactory.comId(USER);
        user.setTimeZone(fuso);
        user.setDailyFocusGoalMinutes(metaMinutos);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
        return user;
    }

    private static GoalResponse meta(long id, GoalStatus status) {
        return new GoalResponse(id, "Meta " + id, null, null, status, 2, 1, 50, AGORA);
    }

    // ---------- hoje ----------

    @Test
    void deveUsarADataLocalDoUsuarioComoHoje() {
        // Given: 01:30 UTC, mas o usuário está em São Paulo, onde ainda é dia 25
        usuario("America/Sao_Paulo", null);
        when(queries.plannedTasks(USER, HOJE_SP)).thenReturn(new PlannedCount(0, 0));

        // When
        DashboardTodayResponse resposta = service.today(USER);

        // Then: todas as consultas usam o dia 25, no fuso de São Paulo
        assertThat(resposta.date()).isEqualTo(HOJE_SP);
        verify(queries).completedTasksByDay(USER, HOJE_SP, HOJE_SP, SAO_PAULO);
        verify(queries).focusSecondsByDay(USER, HOJE_SP, HOJE_SP, SAO_PAULO, AGORA);
    }

    @Test
    void deveUsarOutroDiaQuandoOFusoDoUsuarioJaVirou() {
        // Given: em UTC já é dia 26
        usuario("UTC", null);
        LocalDate hojeUtc = LocalDate.of(2026, 11, 26);
        when(queries.plannedTasks(USER, hojeUtc)).thenReturn(new PlannedCount(0, 0));

        // When / Then
        assertThat(service.today(USER).date()).isEqualTo(hojeUtc);
    }

    @Test
    void deveMontarOResumoDeHojeComOsNumerosDasConsultas() {
        // Given
        usuario("America/Sao_Paulo", 240);
        when(queries.completedTasksByDay(USER, HOJE_SP, HOJE_SP, SAO_PAULO)).thenReturn(Map.of(HOJE_SP, 2L));
        when(queries.focusSecondsByDay(USER, HOJE_SP, HOJE_SP, SAO_PAULO, AGORA)).thenReturn(Map.of(HOJE_SP, 7200L));
        when(queries.plannedTasks(USER, HOJE_SP)).thenReturn(new PlannedCount(4, 3));
        when(goalService.list(USER)).thenReturn(List.of(meta(1, GoalStatus.IN_PROGRESS)));

        // When
        DashboardTodayResponse resposta = service.today(USER);

        // Then
        assertThat(resposta.tasksCompleted()).isEqualTo(2);
        assertThat(resposta.focusSeconds()).isEqualTo(7200);
        assertThat(resposta.completionPercent()).isEqualTo(75);
        assertThat(resposta.focusGoalMinutes()).isEqualTo(240);
        assertThat(resposta.focusPercent()).isEqualTo(50);
    }

    @Test
    void deveListarSoAsMetasEmAndamento() {
        // Given
        usuario("America/Sao_Paulo", null);
        when(queries.plannedTasks(USER, HOJE_SP)).thenReturn(new PlannedCount(0, 0));
        when(goalService.list(USER)).thenReturn(List.of(
                meta(1, GoalStatus.IN_PROGRESS), meta(2, GoalStatus.COMPLETED),
                meta(3, GoalStatus.CANCELLED), meta(4, GoalStatus.IN_PROGRESS)));

        // When / Then
        assertThat(service.today(USER).goalsInProgress()).extracting(GoalResponse::id).containsExactly(1L, 4L);
    }

    @Test
    void deveLancarNaoEncontradoQuandoOUsuarioNaoExisteMais() {
        when(userRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.today(USER)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- relatório ----------

    @Test
    void deveUsarOPeriodoAtualQuandoNaoHaDataDeReferencia() {
        // Given: hoje (25/11, quarta) em São Paulo
        usuario("America/Sao_Paulo", null);
        LocalDate segunda = LocalDate.of(2026, 11, 23);
        LocalDate domingo = LocalDate.of(2026, 11, 29);

        // When
        ReportResponse resposta = service.report(USER, ReportPeriod.WEEK, null);

        // Then
        assertThat(resposta.from()).isEqualTo(segunda);
        assertThat(resposta.to()).isEqualTo(domingo);
        verify(queries).focusSecondsByDay(USER, segunda, domingo, SAO_PAULO, AGORA);
        verify(queries).categoryStats(USER, segunda, domingo, SAO_PAULO, AGORA);
    }

    @Test
    void deveUsarOPeriodoQueContemADataInformada() {
        // Given
        usuario("America/Sao_Paulo", null);

        // When
        ReportResponse resposta = service.report(USER, ReportPeriod.MONTH, LocalDate.of(2026, 2, 14));

        // Then
        assertThat(resposta.from()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(resposta.to()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(resposta.summary().elapsedDays()).isEqualTo(28);
    }

    // ---------- calendário ----------

    @Test
    void deveRecusarIntervaloInvertido() {
        assertThatThrownBy(() -> service.calendar(USER, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void deveRecusarIntervaloMaiorQue92Dias() {
        LocalDate de = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> service.calendar(USER, de, de.plusDays(92)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).contains("92"));
    }

    @Test
    void deveAceitarExatamente92Dias() {
        LocalDate de = LocalDate.of(2026, 1, 1);
        when(taskRepository.findAllByUserIdAndPlannedDateBetween(eq(USER), any(), any())).thenReturn(List.of());

        assertThat(service.calendar(USER, de, de.plusDays(91)).days()).hasSize(92);
    }
}
