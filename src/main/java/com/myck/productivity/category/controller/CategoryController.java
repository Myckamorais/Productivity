package com.myck.productivity.category.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.category.dto.CategoryRequest;
import com.myck.productivity.category.dto.CategoryResponse;
import com.myck.productivity.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** Cria uma categoria do usuário logado e retorna os dados criados (201); nome repetido retorna 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(user.id(), request);
    }

    /** Lista as categorias do usuário logado em ordem alfabética. */
    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return categoryService.list(user.id());
    }

    /** Retorna uma categoria do usuário logado; 404 se não existir ou for de outro usuário. */
    @GetMapping("/{id}")
    public CategoryResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return categoryService.get(user.id(), id);
    }

    /** Atualiza nome e cor de uma categoria do usuário e retorna o resultado; 404 ou 409 nos casos de erro. */
    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(user.id(), id, request);
    }

    /** Remove uma categoria do usuário e não retorna corpo (204); 404 se não existir ou for de outro usuário. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        categoryService.delete(user.id(), id);
    }
}
