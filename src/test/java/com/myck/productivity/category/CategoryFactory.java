package com.myck.productivity.category;

import com.myck.productivity.category.entity.Category;

/** Fábrica de categorias para os testes unitários. */
public final class CategoryFactory {

    private CategoryFactory() {
    }

    /** Categoria "Estudos" azul, do usuário e com o id informados. */
    public static Category estudos(Long id, Long userId) {
        Category category = new Category(userId, "Estudos", "#3B82F6");
        category.setId(id);
        return category;
    }
}
