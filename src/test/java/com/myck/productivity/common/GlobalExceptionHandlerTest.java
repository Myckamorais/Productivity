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

import java.time.LocalDate;
import java.util.List;

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
                .andExpect(jsonPath("$.message").value("Corpo da requisição ausente ou JSON malformado."))
                .andExpect(jsonPath("$.path").value("/fake/validacao"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void deveRetornar400QuandoOCorpoEstaAusente() throws Exception {
        mockMvc.perform(post("/fake/validacao").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisição ausente ou JSON malformado."));
    }

    @Test
    void deveApontarOCampoEOsValoresAceitosQuandoOEnumEInvalido() throws Exception {
        mockMvc.perform(post("/fake/tipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\": \"ROXO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos."))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("cor"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Valor inválido. Valores aceitos: AZUL, VERDE."));
    }

    @Test
    void deveApontarOCampoEOFormatoEsperadoQuandoADataEInvalida() throws Exception {
        mockMvc.perform(post("/fake/tipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"31/12/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("data"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Data inválida. Use o formato AAAA-MM-DD."));
    }

    @Test
    void deveApontarOCaminhoCompletoQuandoOCampoInvalidoEstaAninhado() throws Exception {
        mockMvc.perform(post("/fake/tipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\": [{\"cor\": \"AZUL\"}, {\"cor\": \"ROXO\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("itens[1].cor"));
    }

    @Test
    void deveUsarMensagemGenericaDoCampoQuandoOTipoNaoTemMensagemEspecifica() throws Exception {
        mockMvc.perform(post("/fake/tipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": \"muitos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantidade"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Valor inválido para este campo."));
    }

    @Test
    void naoDeveVazarNomesDeClasseDoJacksonNaMensagem() throws Exception {
        mockMvc.perform(post("/fake/tipos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cor\": \"ROXO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.myck"))));
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

        @PostMapping("/fake/tipos")
        void tipos(@RequestBody Tipos corpo) {
        }

        @GetMapping("/fake/erro")
        void erro() {
            throw new IllegalStateException("detalhe interno que não pode vazar");
        }
    }

    record Corpo(@NotBlank(message = "O título é obrigatório.") String titulo) {
    }

    enum Cor {
        AZUL, VERDE
    }

    record Item(Cor cor) {
    }

    /** Corpo com enum, data, número e lista aninhada, para provocar erros de tipo no JSON. */
    record Tipos(Cor cor, LocalDate data, Integer quantidade, List<Item> itens) {
    }
}
