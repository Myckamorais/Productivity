package com.myck.productivity.task;

import com.myck.productivity.task.dto.TaskRequest;
import com.myck.productivity.task.entity.Task;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;

import java.time.LocalDate;

/** Fábrica de tarefas e de corpos de requisição para os testes unitários. */
public final class TaskFactory {

    private TaskFactory() {
    }

    /** Tarefa "Estudar Spring" pendente, prioridade média, categoria 10 e sem meta. */
    public static Task estudarSpring(Long id, Long userId) {
        Task task = new Task(userId, 10L, null, "Estudar Spring", "Capítulo 3", TaskPriority.MEDIUM,
                LocalDate.of(2026, 11, 25));
        task.setId(id);
        return task;
    }

    /** Corpo mínimo de criação: só título e categoria. */
    public static TaskRequest requisicaoMinima(String titulo, Long categoriaId) {
        return new TaskRequest(titulo, null, categoriaId, null, null, null, null);
    }

    /** Corpo completo de edição. */
    public static TaskRequest requisicao(String titulo, Long categoriaId, Long metaId, TaskPriority prioridade,
            TaskStatus status, LocalDate data) {
        return new TaskRequest(titulo, "descrição", categoriaId, metaId, prioridade, status, data);
    }
}
