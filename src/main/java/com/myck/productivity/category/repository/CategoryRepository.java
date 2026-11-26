package com.myck.productivity.category.repository;

import com.myck.productivity.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Toda consulta recebe o id do usuário: é assim que um usuário nunca alcança a categoria de outro.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    // Usada na edição: o próprio registro não conta como duplicado
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
