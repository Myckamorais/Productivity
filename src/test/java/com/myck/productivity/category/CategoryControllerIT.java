package com.myck.productivity.category;

import com.myck.productivity.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerIT extends AbstractIntegrationTest {

    private static String corpo(String nome, String cor) {
        return """
                {"name": "%s", "color": "%s"}""".formatted(nome, cor);
    }

    /** Cria uma categoria pela API e devolve o id. */
    private long criar(String auth, String nome, String cor) throws Exception {
        String resposta = mockMvc.perform(post("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(nome, cor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return lerId(resposta);
    }

    // ---------- fluxo do CRUD ----------

    @Test
    void deveCriarCategoriaERetornar201() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estudos", "#3b82f6")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Estudos"))
                .andExpect(jsonPath("$.color").value("#3B82F6"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void deveListarEmOrdemAlfabeticaApenasAsCategoriasDoUsuario() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criar(auth, "Trabalho", "#EF4444");
        criar(auth, "Estudos", "#3B82F6");

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Estudos"))
                .andExpect(jsonPath("$[1].name").value("Trabalho"));
    }

    @Test
    void deveBuscarAtualizarERemoverACategoria() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long id = criar(auth, "Estudos", "#3B82F6");

        mockMvc.perform(get("/categories/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Estudos"));

        mockMvc.perform(put("/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Faculdade", "#10B981")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Faculdade"))
                .andExpect(jsonPath("$.color").value("#10B981"));

        mockMvc.perform(delete("/categories/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/categories/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
    }

    // ---------- validações e regras ----------

    @Test
    void deveRetornar400ListandoOsCamposInvalidos() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("", "azul")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void deveRetornar409QuandoONomeJaExisteMesmoEmOutraCaixa() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criar(auth, "Estudos", "#3B82F6");

        mockMvc.perform(post("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ESTUDOS", "#EF4444")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe uma categoria com esse nome."));
    }

    @Test
    void deveRetornar409AoRenomearParaONomeDeOutraCategoria() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criar(auth, "Estudos", "#3B82F6");
        long trabalho = criar(auth, "Trabalho", "#EF4444");

        mockMvc.perform(put("/categories/{id}", trabalho)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("estudos", "#EF4444")))
                .andExpect(status().isConflict());
    }

    @Test
    void devePermitirSalvarACategoriaSemMudarONome() throws Exception {
        // Given: editar só a cor mantém o mesmo nome, que não pode contar como duplicado de si mesmo
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long id = criar(auth, "Estudos", "#3B82F6");

        // When / Then
        mockMvc.perform(put("/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estudos", "#000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#000000"));
    }

    @Test
    void deveRetornar404QuandoACategoriaNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/categories/{id}", 999999).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada."));
    }

    // ---------- autenticação ----------

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estudos", "#3B82F6")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- isolamento entre usuários ----------

    @Test
    void devePermitirONomeIgualEmUsuariosDiferentes() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");

        criar(ana, "Estudos", "#3B82F6");
        criar(bruno, "Estudos", "#3B82F6");
    }

    @Test
    void usuarioNaoVeNemListaCategoriasDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long idDaAna = criar(ana, "Estudos", "#3B82F6");

        // O Bruno não enxerga a categoria da Ana: nem na listagem, nem pelo id
        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/categories/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioNaoEditaNemRemoveCategoriaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long idDaAna = criar(ana, "Estudos", "#3B82F6");

        mockMvc.perform(put("/categories/{id}", idDaAna)
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Invadida", "#000000")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/categories/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());

        // A categoria da Ana continua intacta
        mockMvc.perform(get("/categories/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Estudos"));
    }
}
