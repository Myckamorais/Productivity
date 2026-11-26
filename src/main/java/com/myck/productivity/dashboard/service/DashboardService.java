package com.myck.productivity.dashboard.service;

import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.dashboard.dto.CalendarResponse;
import com.myck.productivity.dashboard.dto.DashboardTodayResponse;
import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportResponse;
import com.myck.productivity.dashboard.mapper.DashboardMapper;
import com.myck.productivity.dashboard.repository.DashboardQueries;
import com.myck.productivity.goal.dto.GoalResponse;
import com.myck.productivity.goal.entity.GoalStatus;
import com.myck.productivity.goal.service.GoalService;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.mapper.TaskMapper;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskTimeQueries;
import com.myck.productivity.task.repository.TaskTimeSummary;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Dashboard do dia, relatórios por período e calendário. Tudo é calculado no fuso do próprio usuário,
 * e "hoje" é a data local dele no momento da requisição (uma única leitura do relógio por requisição).
 */
@Service
public class DashboardService {

    /** Maior intervalo do calendário, em dias: cobre uma grade de 3 meses sem respostas gigantes. */
    static final int MAX_CALENDAR_DAYS = 92;

    private final UserRepository userRepository;
    private final DashboardQueries queries;
    private final GoalService goalService;
    private final TaskRepository taskRepository;
    private final TaskTimeQueries taskTimeQueries;
    private final TaskMapper taskMapper;
    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    public DashboardService(UserRepository userRepository, DashboardQueries queries, GoalService goalService,
            TaskRepository taskRepository, TaskTimeQueries taskTimeQueries, TaskMapper taskMapper,
            DashboardMapper dashboardMapper, Clock clock) {
        this.userRepository = userRepository;
        this.queries = queries;
        this.goalService = goalService;
        this.taskRepository = taskRepository;
        this.taskTimeQueries = taskTimeQueries;
        this.taskMapper = taskMapper;
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    /**
     * Resumo de hoje: tarefas concluídas hoje, rendimento das planejadas para hoje, tempo de foco de hoje
     * comparado à meta diária (se houver) e metas em andamento com o progresso.
     */
    @Transactional(readOnly = true)
    public DashboardTodayResponse today(Long userId) {
        User user = findUser(userId);
        ZoneId zone = user.zoneId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);

        long completedToday = queries.completedTasksByDay(userId, today, today, zone).getOrDefault(today, 0L);
        long focusToday = queries.focusSecondsByDay(userId, today, today, zone, now).getOrDefault(today, 0L);
        List<GoalResponse> goalsInProgress = goalService.list(userId).stream()
                .filter(goal -> goal.status() == GoalStatus.IN_PROGRESS)
                .toList();

        return dashboardMapper.toToday(today, zone, completedToday, queries.plannedTasks(userId, today),
                focusToday, user.getDailyFocusGoalMinutes(), goalsInProgress);
    }

    /**
     * Relatório do período de calendário (semana de segunda a domingo, mês ou ano) que contém a data
     * informada; sem data, o período atual. Traz a produtividade de cada dia, as categorias mais
     * produtivas (por tarefas concluídas) e o resumo.
     */
    @Transactional(readOnly = true)
    public ReportResponse report(Long userId, ReportPeriod period, LocalDate referenceDate) {
        User user = findUser(userId);
        ZoneId zone = user.zoneId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        ReportPeriod.DateRange range = period.rangeContaining(referenceDate != null ? referenceDate : today);

        return dashboardMapper.toReport(period, range, zone, today,
                queries.focusSecondsByDay(userId, range.from(), range.to(), zone, now),
                queries.completedTasksByDay(userId, range.from(), range.to(), zone),
                queries.categoryStats(userId, range.from(), range.to(), zone, now));
    }

    /**
     * Tarefas planejadas por dia entre {@code from} e {@code to} (inclusive), cada uma com tempo investido e
     * estado do cronômetro. Intervalo invertido ou maior que 92 dias dá 400.
     */
    @Transactional(readOnly = true)
    public CalendarResponse calendar(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A data inicial não pode ser depois da final.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "O intervalo do calendário pode ter no máximo " + MAX_CALENDAR_DAYS + " dias.");
        }
        List<Task> tasks = taskRepository.findAllByUserIdAndPlannedDateBetween(userId, from, to);
        Map<Long, TaskTimeSummary> times = taskTimeQueries.summaries(
                tasks.stream().map(Task::getId).toList(), clock.instant());
        List<TaskResponse> responses = tasks.stream()
                .map(task -> taskMapper.toResponse(task, times.getOrDefault(task.getId(), TaskTimeSummary.NONE)))
                .toList();
        return dashboardMapper.toCalendar(from, to, responses);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }
}
