package com.myck.productivity.category;

import com.myck.productivity.category.dto.CategoryRequest;
import com.myck.productivity.category.dto.CategoryResponse;
import com.myck.productivity.category.entity.Category;
import com.myck.productivity.category.mapper.CategoryMapper;
import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.category.service.CategoryService;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER = 1L;
    private static final Long OUTRO_USER = 2L;

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, new CategoryMapper());
    }

    // ---------- create ----------

    @Test
    void deveCriarCategoriaDoUsuarioComNomeSemEspacosECorEmMaiusculas() {
        // Given
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER, "Estudos")).thenReturn(false);

        // When
        CategoryResponse resposta = categoryService.create(USER, new CategoryRequest("  Estudos ", "#3b82f6"));

        // Then
        ArgumentCaptor<Category> salva = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getUserId()).isEqualTo(USER);
        assertThat(salva.getValue().getName()).isEqualTo("Estudos");
        assertThat(salva.getValue().getColor()).isEqualTo("#3B82F6");
        assertThat(resposta.name()).isEqualTo("Estudos");
    }

    @Test
    void deveRecusarCriacaoQuandoOUsuarioJaTemCategoriaComOMesmoNome() {
        // Given
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER, "Estudos")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> categoryService.create(USER, new CategoryRequest("Estudos", "#3B82F6")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCriacaoQuandoOBancoRejeitaNomeDuplicadoPorConcorrencia() {
        // Given: a checagem não viu a outra requisição, mas o índice único do banco barrou o INSERT
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER, "Estudos")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        // When / Then
        assertThatThrownBy(() -> categoryService.create(USER, new CategoryRequest("Estudos", "#3B82F6")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------- list / get ----------

    @Test
    void deveListarAsCategoriasDoUsuario() {
        // Given
        when(categoryRepository.findAllByUserIdOrderByNameAsc(USER))
                .thenReturn(List.of(CategoryFactory.estudos(10L, USER)));

        // When
        List<CategoryResponse> lista = categoryService.list(USER);

        // Then
        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().id()).isEqualTo(10L);
    }

    @Test
    void deveRetornarACategoriaQuandoEDoUsuario() {
        // Given
        when(categoryRepository.findByIdAndUserId(10L, USER))
                .thenReturn(Optional.of(CategoryFactory.estudos(10L, USER)));

        // When / Then
        assertThat(categoryService.get(USER, 10L).name()).isEqualTo("Estudos");
    }

    @Test
    void deveLancarNaoEncontradoAoBuscarCategoriaDeOutroUsuario() {
        // Given: o repositório filtra por dono, então a categoria de outro usuário "não existe" para este
        when(categoryRepository.findByIdAndUserId(10L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> categoryService.get(OUTRO_USER, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void deveAtualizarNomeECorDaCategoria() {
        // Given
        Category categoria = CategoryFactory.estudos(10L, USER);
        when(categoryRepository.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(categoria));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER, "Trabalho", 10L)).thenReturn(false);

        // When
        CategoryResponse resposta = categoryService.update(USER, 10L, new CategoryRequest("Trabalho", "#ef4444"));

        // Then
        assertThat(resposta.name()).isEqualTo("Trabalho");
        assertThat(resposta.color()).isEqualTo("#EF4444");
        verify(categoryRepository).saveAndFlush(categoria);
    }

    @Test
    void deveRecusarAtualizacaoParaNomeJaUsadoPorOutraCategoriaDoUsuario() {
        // Given
        when(categoryRepository.findByIdAndUserId(10L, USER))
                .thenReturn(Optional.of(CategoryFactory.estudos(10L, USER)));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER, "Trabalho", 10L)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> categoryService.update(USER, 10L, new CategoryRequest("Trabalho", "#EF4444")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveLancarNaoEncontradoAoAtualizarCategoriaDeOutroUsuario() {
        // Given
        when(categoryRepository.findByIdAndUserId(10L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> categoryService.update(OUTRO_USER, 10L, new CategoryRequest("X", "#EF4444")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    // ---------- delete ----------

    @Test
    void deveRemoverACategoriaDoUsuario() {
        // Given
        Category categoria = CategoryFactory.estudos(10L, USER);
        when(categoryRepository.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(categoria));

        // When
        categoryService.delete(USER, 10L);

        // Then
        verify(categoryRepository).delete(categoria);
    }

    @Test
    void naoDeveRemoverCategoriaDeOutroUsuario() {
        // Given
        when(categoryRepository.findByIdAndUserId(10L, OUTRO_USER)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> categoryService.delete(OUTRO_USER, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).delete(any());
    }
}
