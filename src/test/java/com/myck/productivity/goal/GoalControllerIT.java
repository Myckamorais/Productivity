package com.myck.productivity.goal;

import com.myck.productivity.AbstractIntegrationTest;
import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.goal.repository.GoalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalControllerIT extends AbstractIntegrationTest {

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private static final String META_COMPLETA = """
            {"title": "Aprender Java", "description": "Concluir o curso", "deadline": "2026-12-31", "status": "IN_PROGRESS"}""";

    private static String corpo(String titulo) {
        return """
                {"title": "%s"}""".formatted(titulo);
    }

    /** Cria uma meta pela API com o corpo informado e devolve o id. */
    private long criar(String auth, String corpo) throws Exception {
        String resposta = mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return lerId(resposta);
    }

    // ---------- fluxo do CRUD ----------

    @Test
    void deveCriarMetaCompletaERetornar201() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(META_COMPLETA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Aprender Java"))
                .andExpect(jsonPath("$.description").value("Concluir o curso"))
                .andExpect(jsonPath("$.deadline").value("2026-12-31"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void deveCriarMetaSoComTituloEmAndamentoESemPrazo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Ler 12 livros")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deadline").doesNotExist());
    }

    @Test
    void deveListarAsMetasMaisRecentesPrimeiro() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criar(auth, corpo("Primeira"));
        criar(auth, corpo("Segunda"));

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Segunda"))
                .andExpect(jsonPath("$[1].title").value("Primeira"));
    }

    @Test
    void deveBuscarAtualizarERemoverAMeta() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long id = criar(auth, META_COMPLETA);

        mockMvc.perform(get("/goals/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aprender Java"));

        mockMvc.perform(put("/goals/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Aprender Spring", "status": "COMPLETED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aprender Spring"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.deadline").doesNotExist());

        mockMvc.perform(delete("/goals/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/goals/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveManterOStatusQuandoAEdicaoNaoInformaStatus() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long id = criar(auth, """
                {"title": "Meta", "status": "CANCELLED"}""");

        mockMvc.perform(put("/goals/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Meta renomeada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- progresso (tarefas concluídas / total) ----------

    private void concluir(String auth, long tarefa) throws Exception {
        mockMvc.perform(patch("/tasks/{id}/complete", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
    }

    @Test
    void deveMostrarProgressoZeroQuandoAMetaNaoTemTarefas() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long meta = criar(auth, corpo("Sem tarefas"));

        mockMvc.perform(get("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0));
    }

    @Test
    void deveAtualizarOProgressoAConformeAsTarefasSaoConcluidas() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long meta = criar(auth, corpo("Aprender Java"));
        long t1 = criarTarefa(auth, "T1", categoria, meta, null);
        long t2 = criarTarefa(auth, "T2", categoria, meta, null);
        long t3 = criarTarefa(auth, "T3", categoria, meta, null);

        // Nenhuma concluída: 0 de 3
        mockMvc.perform(get("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0));

        // 1 de 3 = 33%
        concluir(auth, t1);
        mockMvc.perform(get("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.progressPercent").value(33));

        // 2 de 3 = 67%
        concluir(auth, t2);
        mockMvc.perform(get("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.progressPercent").value(67));

        // 3 de 3 = 100%
        concluir(auth, t3);
        mockMvc.perform(get("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    void deveContarSoAsTarefasDeCadaMetaNaListagem() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long metaA = criar(auth, corpo("Meta A"));
        long metaB = criar(auth, corpo("Meta B"));
        long metaVazia = criar(auth, corpo("Meta vazia"));
        long a1 = criarTarefa(auth, "A1", categoria, metaA, null);
        criarTarefa(auth, "A2", categoria, metaA, null);
        long b1 = criarTarefa(auth, "B1", categoria, metaB, null);
        criarTarefa(auth, "Solta", categoria, null, null);
        concluir(auth, a1);
        concluir(auth, b1);

        // A listagem vem das mais recentes: vazia, B, A
        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[0].id").value(metaVazia))
                .andExpect(jsonPath("$[0].progressPercent").value(0))
                .andExpect(jsonPath("$[1].id").value(metaB))
                .andExpect(jsonPath("$[1].totalTasks").value(1))
                .andExpect(jsonPath("$[1].progressPercent").value(100))
                .andExpect(jsonPath("$[2].id").value(metaA))
                .andExpect(jsonPath("$[2].totalTasks").value(2))
                .andExpect(jsonPath("$[2].progressPercent").value(50));
    }

    @Test
    void deveDesvincularAsTarefasEMantelasQuandoAMetaEApagada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long meta = criar(auth, corpo("Aprender Java"));
        long tarefa = criarTarefa(auth, "Estudar", categoria, meta, null);

        mockMvc.perform(delete("/goals/{id}", meta).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        // A tarefa continua existindo, só sem meta
        mockMvc.perform(get("/tasks/{id}", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Estudar"))
                .andExpect(jsonPath("$.goalId").doesNotExist());
    }

    // ---------- validações ----------

    @Test
    void deveRetornar400QuandoOTituloEstaEmBranco() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void deveRetornar400QuandoOStatusNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Meta", "status": "INVENTADO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Valor inválido. Valores aceitos: IN_PROGRESS, COMPLETED, CANCELLED."));
    }

    @Test
    void deveRetornar400QuandoADataEstaEmFormatoInvalido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Meta", "deadline": "31/12/2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("deadline"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Data inválida. Use o formato AAAA-MM-DD."));
    }

    @Test
    void deveRetornar404QuandoAMetaNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/goals/{id}", 999999).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Meta não encontrada."));
    }

    // ---------- autenticação ----------

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/goals")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Meta")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- isolamento entre usuários ----------

    @Test
    void usuarioNaoVeNemListaMetasDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long idDaAna = criar(ana, META_COMPLETA);

        mockMvc.perform(get("/goals").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/goals/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioNaoEditaNemRemoveMetaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long idDaAna = criar(ana, META_COMPLETA);

        mockMvc.perform(put("/goals/{id}", idDaAna)
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Invadida")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/goals/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());

        // A meta da Ana continua intacta
        mockMvc.perform(get("/goals/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aprender Java"));
    }

    @Test
    void deveApagarCategoriasEMetasQuandoOUsuarioEApagado() throws Exception {
        // Given: usuário com uma meta e uma categoria
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criar(auth, META_COMPLETA);
        mockMvc.perform(post("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Estudos", "color": "#3B82F6"}"""))
                .andExpect(status().isCreated());

        // When: a conta é removida (ON DELETE CASCADE no banco)
        userRepository.deleteAll();

        // Then: não sobram registros órfãos
        assertThat(goalRepository.count()).isZero();
        assertThat(categoryRepository.count()).isZero();
    }
}
