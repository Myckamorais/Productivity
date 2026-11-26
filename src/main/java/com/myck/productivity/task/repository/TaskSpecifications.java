package com.myck.productivity.task.repository;

import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Monta a consulta da listagem de tarefas: o filtro por usuário é sempre aplicado e os demais
 * (status, categoria, meta e intervalo de datas) só entram quando informados. Specification evita
 * a consulta fixa com "(:param is null or ...)", que o PostgreSQL trata mal com parâmetros nulos.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /** Tarefas do usuário, filtradas pelos critérios não nulos; o intervalo de datas é inclusivo nas duas pontas. */
    public static Specification<Task> filter(Long userId, TaskStatus status, Long categoryId, Long goalId,
            LocalDate from, LocalDate to) {
        Specification<Task> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (goalId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("goalId"), goalId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("plannedDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("plannedDate"), to));
        }
        return spec;
    }
}
