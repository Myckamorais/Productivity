package com.myck.productivity.task.repository;

import com.myck.productivity.task.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {

    // A sessão aberta do usuário, em qualquer tarefa. Há no máximo uma (índice único parcial no banco)
    Optional<TaskSession> findByUserIdAndEndedAtIsNull(Long userId);

    // A sessão aberta de uma tarefa; o chamador já validou que a tarefa é do usuário
    Optional<TaskSession> findByTaskIdAndEndedAtIsNull(Long taskId);

    // Todas as sessões de uma tarefa, da mais antiga para a mais recente
    List<TaskSession> findAllByTaskIdOrderByStartedAtAsc(Long taskId);
}
