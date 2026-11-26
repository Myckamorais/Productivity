package com.myck.productivity.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Categoria de tarefas (ex.: Estudos, Trabalho), com uma cor para os gráficos e telas. */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Guarda só o id do dono (sem @ManyToOne): toda consulta já filtra por ele e evita carregar o User à toa
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String name;

    // Hexadecimal no formato #RRGGBB
    @Column(nullable = false, length = 7)
    private String color;

    // Truncado em microssegundos, a precisão do PostgreSQL: assim o valor devolvido na criação é idêntico ao das leituras seguintes
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public Category(Long userId, String name, String color) {
        this.userId = userId;
        this.name = name;
        this.color = color;
    }
}
