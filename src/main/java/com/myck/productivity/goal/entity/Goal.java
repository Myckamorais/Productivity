package com.myck.productivity.goal.entity;

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

/** Meta: apenas agrupa tarefas. O progresso é calculado a partir delas (Fase 3). */
@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Só o id do dono (sem @ManyToOne): toda consulta já filtra por ele
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    // Opcional: nem toda meta tem data limite
    private LocalDate deadline;

    // Gravado como texto (e não como número), para reordenar o enum no futuro não corromper os dados
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status;

    // Truncado em microssegundos, a precisão do PostgreSQL: assim o valor devolvido na criação é idêntico ao das leituras seguintes
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public Goal(Long userId, String title, String description, LocalDate deadline, GoalStatus status) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.deadline = deadline;
        this.status = status;
    }
}
