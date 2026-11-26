package com.myck.productivity.task.repository;

import com.myck.productivity.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Toda consulta recebe o id do usuário: um usuário nunca alcança a tarefa de outro.
 * A listagem filtrada usa Specification (ver {@link TaskSpecifications}).
 */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    // Tarefas do usuário planejadas dentro do intervalo de datas (inclusivo), para o calendário
    List<Task> findAllByUserIdAndPlannedDateBetween(Long userId, LocalDate from, LocalDate to);

    // Usada para impedir a exclusão de uma categoria que ainda tem tarefas
    boolean existsByCategoryId(Long categoryId);

    // Para cada meta informada, conta o total de tarefas e quantas estão concluídas, numa única consulta
    // agrupada (evita uma consulta por meta na listagem). Metas sem tarefas não aparecem no resultado.
    @Query("""
            select new com.myck.productivity.task.repository.GoalTaskCount(
                t.goalId,
                count(t),
                count(case when t.status = com.myck.productivity.task.entity.TaskStatus.COMPLETED then 1 end))
            from Task t
            where t.userId = :userId and t.goalId in :goalIds
            group by t.goalId
            """)
    List<GoalTaskCount> countByGoals(@Param("userId") Long userId, @Param("goalIds") Collection<Long> goalIds);
}
