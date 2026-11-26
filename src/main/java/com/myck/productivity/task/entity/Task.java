package com.myck.productivity.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Tarefa do usuário: pertence a uma categoria e, opcionalmente, a uma meta. */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Só os ids (sem @ManyToOne): toda consulta já filtra por usuário e a API devolve apenas os ids
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // Opcional: nem toda tarefa pertence a uma meta
    @Column(name = "goal_id")
    private Long goalId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    // Momento da conclusão; fica nulo enquanto a tarefa não está concluída
    @Column(name = "completed_at")
    private Instant completedAt;

    // Truncado em microssegundos, a precisão do PostgreSQL: assim o valor devolvido na criação é idêntico ao das leituras seguintes
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public Task(Long userId, Long categoryId, Long goalId, String title, String description,
            TaskPriority priority, LocalDate plannedDate) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.goalId = goalId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.plannedDate = plannedDate;
    }

    /**
     * Troca o status mantendo o completedAt coerente: marca o instante ao concluir (sem sobrescrever
     * se já estava concluída) e apaga ao reabrir, para a data de conclusão nunca contradizer o status.
     */
    public void changeStatus(TaskStatus newStatus, Instant now) {
        if (newStatus == TaskStatus.COMPLETED) {
            if (completedAt == null) {
                // Mesma regra de precisão do createdAt
                completedAt = now.truncatedTo(ChronoUnit.MICROS);
            }
        } else {
            completedAt = null;
        }
        this.status = newStatus;
    }
}
