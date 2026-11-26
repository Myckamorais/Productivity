package com.myck.productivity.dashboard;

import com.myck.productivity.AbstractFixedClockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /calendar: tarefas por dia pela data planejada. */
class CalendarIT extends AbstractFixedClockIntegrationTest {

    private ResultActions calendario(String auth, String de, String ate) throws Exception {
        return mockMvc.perform(get("/calendar").param("from", de).param("to", ate)
                .header(HttpHeaders.AUTHORIZATION, auth));
    }

    private long tarefaComPrioridade(String auth, String titulo, long categoria, String data, String prioridade)
            throws Exception {
        String resposta = mockMvc.perform(post("/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "categoryId": %d, "plannedDate": "%s", "priority": "%s"}"""
                                .formatted(titulo, categoria, data, prioridade)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return lerId(resposta);
    }

    @Test
    void deveAgruparAsTarefasPorDiaComTodosOsDiasDoIntervalo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long ana = idDoUsuario("ana@exemplo.com");
        long categoria = criarCategoria(auth, "Estudos");
        tarefaComPrioridade(auth, "Segunda", categoria, "2026-11-23", "MEDIUM");
        long baixa = tarefaComPrioridade(auth, "Quarta baixa", categoria, "2026-11-25", "LOW");
        tarefaComPrioridade(auth, "Quarta alta", categoria, "2026-11-25", "HIGH");
        tarefaComPrioridade(auth, "Fora do intervalo", categoria, "2026-12-01", "HIGH");
        criarTarefa(auth, "Sem data", categoria, null, null);
        // 45 min cronometrados na tarefa de prioridade baixa
        sessaoFechada(ana, baixa, sp(2026, 11, 25, 9, 0), sp(2026, 11, 25, 9, 45));

        calendario(auth, "2026-11-23", "2026-11-29")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-23"))
                .andExpect(jsonPath("$.to").value("2026-11-29"))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-23"))
                .andExpect(jsonPath("$.days[0].tasks.length()").value(1))
                // Dia sem tarefas aparece com lista vazia
                .andExpect(jsonPath("$.days[1].tasks.length()").value(0))
                // Quarta: maior prioridade primeiro
                .andExpect(jsonPath("$.days[2].tasks.length()").value(2))
                .andExpect(jsonPath("$.days[2].tasks[0].title").value("Quarta alta"))
                .andExpect(jsonPath("$.days[2].tasks[1].title").value("Quarta baixa"))
                // Cada tarefa vem com o tempo investido e o estado do cronômetro
                .andExpect(jsonPath("$.days[2].tasks[1].totalSeconds").value(2700))
                .andExpect(jsonPath("$.days[2].tasks[1].timerState").value("IDLE"))
                .andExpect(jsonPath("$.days[6].tasks.length()").value(0));
    }

    @Test
    void naoDeveMostrarTarefasDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        criarTarefa(ana, "Da Ana", criarCategoria(ana, "Estudos"), null, "2026-11-25");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");

        calendario(bruno, "2026-11-25", "2026-11-25")
                .andExpect(jsonPath("$.days[0].tasks.length()").value(0));
    }

    @Test
    void deveRetornar400QuandoOIntervaloEInvalido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        calendario(auth, "2026-11-29", "2026-11-23")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data inicial não pode ser depois da final."));
        calendario(auth, "2026-01-01", "2026-04-30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O intervalo do calendário pode ter no máximo 92 dias."));
        mockMvc.perform(get("/calendar").param("from", "2026-11-23").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/calendar").param("from", "2026-11-23").param("to", "2026-11-29"))
                .andExpect(status().isUnauthorized());
    }
}
