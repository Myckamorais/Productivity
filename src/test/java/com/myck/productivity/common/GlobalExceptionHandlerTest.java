package com.myck.productivity.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste unitário do handler: MockMvc standalone, sem subir o Spring nem o banco,
 * com um controller falso que dispara cada tipo de erro.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deveRetornar404ComCorpoPadraoQuandoRecursoNaoExiste() throws Exception {
        // When / Then
        mockMvc.perform(get("/fake/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Tarefa não encontrada."))
                .andExpect(jsonPath("$.path").value("/fake/nao-encontrado"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void deveRetornar422QuandoRegraDeNegocioEViolada() throws Exception {
        mockMvc.perform(get("/fake/regra"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Já existe um cronômetro ativo."));
    }

    @Test
    void deveUsarOStatusDaExcecaoDeNegocioQuandoInformado() throws Exception {
        mockMvc.perform(get("/fake/conflito"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado."));
    }

    @Test
    void deveRetornar400ListandoOsCamposInvalidos() throws Exception {
        // Given: corpo com o campo "titulo" em branco
        // When / Then
        mockMvc.perform(post("/fake/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados inválidos."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("titulo"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("O título é obrigatório."));
    }

    @Test
    void deveRetornar400NoPadraoQuandoOJsonEstaMalformado() throws Exception {
        mockMvc.perform(post("/fake/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{titulo: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/fake/validacao"));
    }

    @Test
    void deveRetornar405NoPadraoQuandoOMetodoNaoEPermitido() throws Exception {
        mockMvc.perform(post("/fake/nao-encontrado"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void deveRetornar500ComMensagemGenericaSemVazarODetalheDoErro() throws Exception {
        mockMvc.perform(get("/fake/erro"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Erro interno do servidor."));
    }

    /** Controller usado só neste teste para provocar cada exceção. */
    @RestController
    static class FakeController {

        @GetMapping("/fake/nao-encontrado")
        void naoEncontrado() {
            throw new ResourceNotFoundException("Tarefa não encontrada.");
        }

        @GetMapping("/fake/regra")
        void regra() {
            throw new BusinessException("Já existe um cronômetro ativo.");
        }

        @GetMapping("/fake/conflito")
        void conflito() {
            throw new BusinessException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }

        @PostMapping("/fake/validacao")
        void validacao(@Valid @RequestBody Corpo corpo) {
        }

        @GetMapping("/fake/erro")
        void erro() {
            throw new IllegalStateException("detalhe interno que não pode vazar");
        }
    }

    record Corpo(@NotBlank(message = "O título é obrigatório.") String titulo) {
    }
}
