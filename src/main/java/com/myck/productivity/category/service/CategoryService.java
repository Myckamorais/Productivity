package com.myck.productivity.category.service;

import com.myck.productivity.category.dto.CategoryRequest;
import com.myck.productivity.category.dto.CategoryResponse;
import com.myck.productivity.category.entity.Category;
import com.myck.productivity.category.mapper.CategoryMapper;
import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.task.repository.TaskRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, TaskRepository taskRepository,
            CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.categoryMapper = categoryMapper;
    }

    /** Cria a categoria do usuário; o nome não pode repetir entre as categorias dele (409), sem diferenciar maiúsculas. */
    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw nameAlreadyUsed();
        }
        Category category = new Category(userId, name, normalizeColor(request.color()));
        save(category);
        return categoryMapper.toResponse(category);
    }

    /** Lista só as categorias do usuário, em ordem alfabética. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    /** Busca uma categoria do usuário; se não existir ou for de outro usuário, a resposta é a mesma (404). */
    @Transactional(readOnly = true)
    public CategoryResponse get(Long userId, Long id) {
        return categoryMapper.toResponse(findOwned(userId, id));
    }

    /** Edita nome e cor; mantém a regra de nome único, ignorando a própria categoria na comparação. */
    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryRequest request) {
        Category category = findOwned(userId, id);
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw nameAlreadyUsed();
        }
        category.setName(name);
        category.setColor(normalizeColor(request.color()));
        save(category);
        return categoryMapper.toResponse(category);
    }

    /**
     * Remove a categoria, desde que seja do usuário e não tenha tarefas (409 caso tenha): a categoria é
     * obrigatória na tarefa, então apagá-la deixaria tarefas sem categoria ou as apagaria sem o usuário perceber.
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwned(userId, id);
        if (taskRepository.existsByCategoryId(id)) {
            throw categoryHasTasks();
        }
        try {
            categoryRepository.delete(category);
            // flush força o DELETE agora: se uma tarefa for criada nesse intervalo, a FK do banco recusa
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw categoryHasTasks();
        }
    }

    // A busca já filtra pelo dono: categoria de outro usuário é tratada como inexistente
    private Category findOwned(Long userId, Long id) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
    }

    // saveAndFlush força o SQL agora: se duas requisições passarem pela checagem ao mesmo tempo,
    // o índice único do banco barra a segunda e ela também recebe 409
    private void save(Category category) {
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw nameAlreadyUsed();
        }
    }

    // Cor sempre em maiúsculas, para #ff0000 e #FF0000 serem iguais
    private static String normalizeColor(String color) {
        return color.toUpperCase();
    }

    private static BusinessException categoryHasTasks() {
        return new BusinessException(HttpStatus.CONFLICT,
                "Não é possível excluir a categoria: ela possui tarefas. Apague ou mude a categoria das tarefas antes.");
    }

    private static BusinessException nameAlreadyUsed() {
        return new BusinessException(HttpStatus.CONFLICT, "Já existe uma categoria com esse nome.");
    }
}
