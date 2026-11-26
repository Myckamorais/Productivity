package com.myck.productivity.task;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.AbstractIntegrationTest;
import com.myck.productivity.task.repository.TaskRepository;
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

class TaskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    private static String corpo(String titulo, long categoriaId) {
        return """
                {"title": "%s", "categoryId": %d}""".formatted(titulo, categoriaId);
    }

    // ---------- criação ----------

    @Test
    void deveCriarTarefaCompletaERetornar201() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long meta = criarMeta(auth, "Aprender Java");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Estudar Spring", "description": "Capítulo 3", "categoryId": %d,
                                 "goalId": %d, "priority": "HIGH", "plannedDate": "2026-11-25"}"""
                                .formatted(categoria, meta)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Estudar Spring"))
                .andExpect(jsonPath("$.description").value("Capítulo 3"))
                .andExpect(jsonPath("$.categoryId").value(categoria))
                .andExpect(jsonPath("$.goalId").value(meta))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.plannedDate").value("2026-11-25"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void deveCriarTarefaSoComTituloECategoriaComValoresPadrao() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Ler artigo", categoria)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.plannedDate").doesNotExist());
    }

    @Test
    void deveDevolverOMesmoCreatedAtNaCriacaoENaLeituraSeguinte() throws Exception {
        // Given: o banco guarda microssegundos; o valor da criação não pode ter mais precisão que o das leituras
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        String criada = mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estudar", categoria)))
                .andReturn().getResponse().getContentAsString();

        // When
        String lida = mockMvc.perform(get("/tasks/{id}", lerId(criada)).header(HttpHeaders.AUTHORIZATION, auth))
                .andReturn().getResponse().getContentAsString();

        // Then
        assertThat(JsonPath.<String>read(lida, "$.createdAt")).isEqualTo(JsonPath.<String>read(criada, "$.createdAt"));
    }

    @Test
    void deveRetornar400ListandoTituloECategoriaObrigatorios() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void deveRetornar400ComOsValoresAceitosQuandoAPrioridadeNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X", "categoryId": %d, "priority": "URGENTE"}""".formatted(categoria)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priority"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("Valor inválido. Valores aceitos: LOW, MEDIUM, HIGH."));
    }

    @Test
    void deveRetornar404QuandoACategoriaNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("X", 999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada."));
    }

    @Test
    void deveRetornar404QuandoAMetaNaoExiste() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");

        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X", "categoryId": %d, "goalId": 999999}""".formatted(categoria)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Meta não encontrada."));
    }

    // ---------- busca, edição, conclusão e remoção ----------

    @Test
    void deveBuscarAtualizarERemoverATarefa() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long estudos = criarCategoria(auth, "Estudos");
        long trabalho = criarCategoria(auth, "Trabalho");
        long id = criarTarefa(auth, "Estudar", estudos, null, "2026-11-25");

        mockMvc.perform(get("/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Estudar"));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Reunião", "categoryId": %d, "priority": "LOW",
                                 "status": "IN_PROGRESS", "plannedDate": "2026-12-01"}""".formatted(trabalho)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Reunião"))
                .andExpect(jsonPath("$.categoryId").value(trabalho))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.plannedDate").value("2026-12-01"));

        mockMvc.perform(delete("/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tarefa não encontrada."));
    }

    @Test
    void deveDesvincularAMetaQuandoAEdicaoNaoInformaGoalId() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long meta = criarMeta(auth, "Aprender Java");
        long id = criarTarefa(auth, "Estudar", categoria, meta, null);

        mockMvc.perform(put("/tasks/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estudar", categoria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").doesNotExist());
    }

    @Test
    void deveConcluirATarefaERegistrarOMomentoSemMudarloAoConcluirDeNovo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long id = criarTarefa(auth, "Estudar", categoria, null, null);

        String primeira = mockMvc.perform(patch("/tasks/{id}/complete", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Concluir outra vez mantém o momento original
        String segunda = mockMvc.perform(patch("/tasks/{id}/complete", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(segunda, "$.completedAt"))
                .isEqualTo(JsonPath.<String>read(primeira, "$.completedAt"));
    }

    @Test
    void deveApagarOMomentoDaConclusaoAoReabrirPeloPut() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long id = criarTarefa(auth, "Estudar", categoria, null, null);
        mockMvc.perform(patch("/tasks/{id}/complete", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        mockMvc.perform(put("/tasks/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Estudar", "categoryId": %d, "status": "PENDING"}""".formatted(categoria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    // ---------- listagem: filtros ----------

    @Test
    void deveFiltrarPorStatus() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        criarTarefa(auth, "Pendente", categoria, null, null);
        long concluida = criarTarefa(auth, "Concluída", categoria, null, null);
        mockMvc.perform(patch("/tasks/{id}/complete", concluida).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks").param("status", "COMPLETED").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Concluída"));
        mockMvc.perform(get("/tasks").param("status", "PENDING").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Pendente"));
    }

    @Test
    void deveFiltrarPorCategoria() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long estudos = criarCategoria(auth, "Estudos");
        long trabalho = criarCategoria(auth, "Trabalho");
        criarTarefa(auth, "De estudos", estudos, null, null);
        criarTarefa(auth, "De trabalho", trabalho, null, null);

        mockMvc.perform(get("/tasks").param("categoryId", String.valueOf(trabalho))
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("De trabalho"));
    }

    @Test
    void deveFiltrarPorMeta() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        long meta = criarMeta(auth, "Aprender Java");
        criarTarefa(auth, "Da meta", categoria, meta, null);
        criarTarefa(auth, "Sem meta", categoria, null, null);

        mockMvc.perform(get("/tasks").param("goalId", String.valueOf(meta))
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Da meta"));
    }

    @Test
    void deveFiltrarPorIntervaloDeDatasIncluindoAsDuasPontas() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        criarTarefa(auth, "Dia 1", categoria, null, "2026-12-01");
        criarTarefa(auth, "Dia 2", categoria, null, "2026-12-02");
        criarTarefa(auth, "Dia 3", categoria, null, "2026-12-03");
        criarTarefa(auth, "Dia 4", categoria, null, "2026-12-04");
        criarTarefa(auth, "Sem data", categoria, null, null);

        // De 2 a 3: inclui os dois dias das pontas e deixa de fora as outras e a sem data
        mockMvc.perform(get("/tasks").param("from", "2026-12-02").param("to", "2026-12-03")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Dia 2"))
                .andExpect(jsonPath("$.content[1].title").value("Dia 3"));

        // Só "from": dia 3 em diante
        mockMvc.perform(get("/tasks").param("from", "2026-12-03").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Só "to": até o dia 2
        mockMvc.perform(get("/tasks").param("to", "2026-12-02").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void deveCombinarVariosFiltros() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long estudos = criarCategoria(auth, "Estudos");
        long trabalho = criarCategoria(auth, "Trabalho");
        criarTarefa(auth, "Estudos dia 1", estudos, null, "2026-12-01");
        criarTarefa(auth, "Estudos dia 5", estudos, null, "2026-12-05");
        criarTarefa(auth, "Trabalho dia 1", trabalho, null, "2026-12-01");

        mockMvc.perform(get("/tasks")
                        .param("categoryId", String.valueOf(estudos))
                        .param("from", "2026-12-01").param("to", "2026-12-02")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Estudos dia 1"));
    }

    @Test
    void deveRetornarPaginaVaziaQuandoNenhumaTarefaCombinaComOFiltro() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        criarTarefa(auth, "Pendente", categoria, null, null);

        mockMvc.perform(get("/tasks").param("status", "COMPLETED").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ---------- listagem: paginação e ordem ----------

    @Test
    void devePaginarOsResultados() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        for (int i = 1; i <= 5; i++) {
            criarTarefa(auth, "Tarefa " + i, categoria, null, "2026-12-0" + i);
        }

        mockMvc.perform(get("/tasks").param("size", "2").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Tarefa 1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/tasks").param("page", "1").param("size", "2").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.content[0].title").value("Tarefa 3"))
                .andExpect(jsonPath("$.content[1].title").value("Tarefa 4"));

        // Última página, incompleta
        mockMvc.perform(get("/tasks").param("page", "2").param("size", "2").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Tarefa 5"));
    }

    @Test
    void deveUsarPaginaDeTamanho20QuandoNaoInformado() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void deveOrdenarPorDataPlanejadaComAsSemDataPorUltimo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        criarTarefa(auth, "Sem data", categoria, null, null);
        criarTarefa(auth, "Dia 5", categoria, null, "2026-12-05");
        criarTarefa(auth, "Dia 1", categoria, null, "2026-12-01");

        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.content[0].title").value("Dia 1"))
                .andExpect(jsonPath("$.content[1].title").value("Dia 5"))
                .andExpect(jsonPath("$.content[2].title").value("Sem data"));
    }

    // ---------- listagem: parâmetros inválidos ----------

    @Test
    void deveRetornar400QuandoOTamanhoDaPaginaEInvalido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/tasks").param("size", "0").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/tasks").param("size", "101").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O parâmetro size deve estar entre 1 e 100."));
    }

    @Test
    void deveRetornar400QuandoAPaginaENegativa() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/tasks").param("page", "-1").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoADataInicialEDepoisDaFinal() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/tasks").param("from", "2026-12-10").param("to", "2026-12-01")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data inicial não pode ser depois da final."));
    }

    @Test
    void deveRetornar400QuandoOStatusOuADataDoFiltroSaoInvalidos() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/tasks").param("status", "INVENTADO").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/tasks").param("from", "31/12/2026").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    // ---------- autenticação ----------

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpo("X", 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/tasks/1/complete")).andExpect(status().isUnauthorized());
    }

    // ---------- isolamento entre usuários ----------

    @Test
    void usuarioSoListaAsPropriasTarefas() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        criarTarefa(ana, "Da Ana", criarCategoria(ana, "Estudos"), null, null);
        criarTarefa(bruno, "Do Bruno", criarCategoria(bruno, "Estudos"), null, null);

        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Do Bruno"));
    }

    @Test
    void usuarioNaoVeEditaConcluiNemRemoveTarefaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long categoriaDoBruno = criarCategoria(bruno, "Estudos");
        long idDaAna = criarTarefa(ana, "Da Ana", criarCategoria(ana, "Estudos"), null, null);

        mockMvc.perform(get("/tasks/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/tasks/{id}", idDaAna)
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Invadida", categoriaDoBruno)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/tasks/{id}/complete", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/tasks/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());

        // A tarefa da Ana continua intacta e pendente
        mockMvc.perform(get("/tasks/{id}", idDaAna).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Da Ana"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void usuarioNaoCriaTarefaEmCategoriaOuMetaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long categoriaDaAna = criarCategoria(ana, "Estudos");
        long metaDaAna = criarMeta(ana, "Meta da Ana");
        long categoriaDoBruno = criarCategoria(bruno, "Estudos");

        // Categoria da Ana na tarefa do Bruno
        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Invasão", categoriaDaAna)))
                .andExpect(status().isNotFound());

        // Meta da Ana na tarefa do Bruno
        mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Invasão", "categoryId": %d, "goalId": %d}"""
                                .formatted(categoriaDoBruno, metaDaAna)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void usuarioNaoMoveSuaTarefaParaCategoriaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long categoriaDaAna = criarCategoria(ana, "Estudos");
        long idDoBruno = criarTarefa(bruno, "Do Bruno", criarCategoria(bruno, "Estudos"), null, null);

        mockMvc.perform(put("/tasks/{id}", idDoBruno)
                        .header(HttpHeaders.AUTHORIZATION, bruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Do Bruno", categoriaDaAna)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveApagarAsTarefasQuandoOUsuarioEApagado() throws Exception {
        // Given
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        criarTarefa(auth, "Estudar", criarCategoria(auth, "Estudos"), null, null);

        // When: a conta é removida (cascade no banco, sem a FK da categoria atrapalhar)
        userRepository.deleteAll();

        // Then
        assertThat(taskRepository.count()).isZero();
    }
}
