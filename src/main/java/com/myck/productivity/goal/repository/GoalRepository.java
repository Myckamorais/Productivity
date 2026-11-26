package com.myck.productivity.goal.repository;

import com.myck.productivity.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Toda consulta recebe o id do usuário: um usuário nunca alcança a meta de outro. */
public interface GoalRepository extends JpaRepository<Goal, Long> {

    // Mais recentes primeiro
    List<Goal> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
