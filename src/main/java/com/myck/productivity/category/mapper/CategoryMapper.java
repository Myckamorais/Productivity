package com.myck.productivity.category.mapper;

import com.myck.productivity.category.dto.CategoryResponse;
import com.myck.productivity.category.entity.Category;
import org.springframework.stereotype.Component;

/** Converte a entidade em DTO: a entidade nunca sai pela API. */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor(),
                category.getCreatedAt());
    }
}
