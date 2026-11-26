package com.myck.productivity.user.entity;

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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Usuário da aplicação. Cada usuário enxerga somente os próprios dados. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Sempre salvo em minúsculas, para "Ana@x.com" e "ana@x.com" serem o mesmo usuário
    @Column(nullable = false, unique = true)
    private String email;

    // Só o hash BCrypt; a senha original nunca é guardada
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // Truncado em microssegundos, a precisão do PostgreSQL: assim o valor devolvido na criação é idêntico ao das leituras seguintes
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    // Fuso do usuário: define onde começa e termina o "dia" no dashboard, relatórios e calendário
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    // Meta diária de foco, em minutos; nula quando o usuário não definiu uma
    @Column(name = "daily_focus_goal_minutes")
    private Integer dailyFocusGoalMinutes;

    /** Fuso usado quando o usuário não informa um. */
    public static final String DEFAULT_TIME_ZONE = "America/Sao_Paulo";

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /** O fuso do usuário como ZoneId, pronto para os cálculos de data. */
    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }
}
