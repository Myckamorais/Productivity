package com.myck.productivity.dashboard;

import com.myck.productivity.AbstractFixedClockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /reports com massa de dados conhecida; hoje é quarta, 25/11/2026 (semana de 23 a 29/11). */
class ReportIT extends AbstractFixedClockIntegrationTest {

    private ResultActions relatorio(String auth, String periodo, String data) throws Exception {
        var requisicao = get("/reports").param("period", periodo).header(HttpHeaders.AUTHORIZATION, auth);
        if (data != null) {
            requisicao.param("date", data);
        }
        return mockMvc.perform(requisicao);
    }

    /**
     * Cenário da Ana (horários de São Paulo):
     * <pre>
     * Sessões
     *   E1 (Estudos)  dom 22 23:00 → seg 23 01:00   1h no domingo (semana anterior) e 1h na segunda
     *   E1 (Estudos)  seg 23 10:00 → 11:00          1h
     *   W1 (Trabalho) ter 24 14:00 → 14:30          30 min
     *   E2 (Estudos)  qua 25 09:00 → 10:30          1h30
     * Conclusões
     *   W1 seg 23 18:00 · W2 ter 24 08:00 · E1 ter 24 20:00 · W3 qua 25 11:00 · E3 sex 20 (semana anterior)
     * Lazer: categoria sem nenhuma atividade
     * </pre>
     * Semana atual esperada:
     *   seg: 1 concluída, 2h · ter: 2 concluídas, 30 min · qua: 1 concluída, 1h30 · qui a dom: zero
     *   Trabalho: 3 concluídas, 30 min · Estudos: 1 concluída, 3h30
     */
    private String cenarioDaAna() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long ana = idDoUsuario("ana@exemplo.com");
        long estudos = criarCategoria(auth, "Estudos");
        long trabalho = criarCategoria(auth, "Trabalho");
        criarCategoria(auth, "Lazer");

        long e1 = criarTarefa(auth, "E1", estudos, null, null);
        long e2 = criarTarefa(auth, "E2", estudos, null, null);
        long e3 = criarTarefa(auth, "E3", estudos, null, null);
        long w1 = criarTarefa(auth, "W1", trabalho, null, null);
        long w2 = criarTarefa(auth, "W2", trabalho, null, null);
        long w3 = criarTarefa(auth, "W3", trabalho, null, null);

        sessaoFechada(ana, e1, sp(2026, 11, 22, 23, 0), sp(2026, 11, 23, 1, 0));
        sessaoFechada(ana, e1, sp(2026, 11, 23, 10, 0), sp(2026, 11, 23, 11, 0));
        sessaoFechada(ana, w1, sp(2026, 11, 24, 14, 0), sp(2026, 11, 24, 14, 30));
        sessaoFechada(ana, e2, sp(2026, 11, 25, 9, 0), sp(2026, 11, 25, 10, 30));

        concluidaEm(w1, sp(2026, 11, 23, 18, 0));
        concluidaEm(w2, sp(2026, 11, 24, 8, 0));
        concluidaEm(e1, sp(2026, 11, 24, 20, 0));
        concluidaEm(w3, sp(2026, 11, 25, 11, 0));
        concluidaEm(e3, sp(2026, 11, 20, 16, 0));
        return auth;
    }

    @Test
    void deveMontarORelatorioDaSemanaAtualComOsNumerosConferidos() throws Exception {
        String auth = cenarioDaAna();

        relatorio(auth, "WEEK", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEK"))
                .andExpect(jsonPath("$.from").value("2026-11-23"))
                .andExpect(jsonPath("$.to").value("2026-11-29"))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                // Um item por dia, inclusive os futuros
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.daily[0].date").value("2026-11-23"))
                .andExpect(jsonPath("$.daily[0].tasksCompleted").value(1))
                .andExpect(jsonPath("$.daily[0].focusSeconds").value(7200))     // 1h da virada + 1h
                .andExpect(jsonPath("$.daily[1].tasksCompleted").value(2))
                .andExpect(jsonPath("$.daily[1].focusSeconds").value(1800))
                .andExpect(jsonPath("$.daily[2].tasksCompleted").value(1))
                .andExpect(jsonPath("$.daily[2].focusSeconds").value(5400))
                .andExpect(jsonPath("$.daily[3].tasksCompleted").value(0))
                .andExpect(jsonPath("$.daily[6].focusSeconds").value(0))
                // Resumo: média sobre os 3 dias já decorridos (seg, ter, qua)
                .andExpect(jsonPath("$.summary.tasksCompleted").value(4))
                .andExpect(jsonPath("$.summary.focusSeconds").value(14400))
                .andExpect(jsonPath("$.summary.activeDays").value(3))
                .andExpect(jsonPath("$.summary.elapsedDays").value(3))
                .andExpect(jsonPath("$.summary.averageFocusSecondsPerDay").value(4800))
                // Ranking por tarefas concluídas: Trabalho (3) antes de Estudos (1), mesmo com menos foco; Lazer fica de fora
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("Trabalho"))
                .andExpect(jsonPath("$.categories[0].tasksCompleted").value(3))
                .andExpect(jsonPath("$.categories[0].focusSeconds").value(1800))
                .andExpect(jsonPath("$.categories[0].color").value("#3B82F6"))
                .andExpect(jsonPath("$.categories[1].name").value("Estudos"))
                .andExpect(jsonPath("$.categories[1].tasksCompleted").value(1))
                .andExpect(jsonPath("$.categories[1].focusSeconds").value(12600));
    }

    @Test
    void deveMontarASemanaAnteriorPelaDataDeReferencia() throws Exception {
        String auth = cenarioDaAna();

        // Semana de 16 a 22/11: só a 1h de domingo (antes da meia-noite) e a conclusão de E3 na sexta
        relatorio(auth, "WEEK", "2026-11-18")
                .andExpect(jsonPath("$.from").value("2026-11-16"))
                .andExpect(jsonPath("$.to").value("2026-11-22"))
                .andExpect(jsonPath("$.daily[4].tasksCompleted").value(1))
                .andExpect(jsonPath("$.daily[6].focusSeconds").value(3600))
                .andExpect(jsonPath("$.summary.tasksCompleted").value(1))
                .andExpect(jsonPath("$.summary.focusSeconds").value(3600))
                .andExpect(jsonPath("$.summary.elapsedDays").value(7))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Estudos"));
    }

    @Test
    void deveMontarOMesComTodosOsDias() throws Exception {
        String auth = cenarioDaAna();

        relatorio(auth, "MONTH", null)
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.daily.length()").value(30))
                // Semana atual + semana anterior: 5 concluídas; 4h + 1h de foco
                .andExpect(jsonPath("$.summary.tasksCompleted").value(5))
                .andExpect(jsonPath("$.summary.focusSeconds").value(18000))
                .andExpect(jsonPath("$.summary.elapsedDays").value(25))
                .andExpect(jsonPath("$.summary.averageFocusSecondsPerDay").value(720));
    }

    @Test
    void deveMontarOAnoComUmItemPorDia() throws Exception {
        String auth = cenarioDaAna();

        relatorio(auth, "YEAR", null)
                .andExpect(jsonPath("$.from").value("2026-01-01"))
                .andExpect(jsonPath("$.to").value("2026-12-31"))
                .andExpect(jsonPath("$.daily.length()").value(365))
                .andExpect(jsonPath("$.summary.tasksCompleted").value(5))
                .andExpect(jsonPath("$.summary.focusSeconds").value(18000));
    }

    @Test
    void deveDesempatarCategoriasPeloTempoDeFocoEDepoisPeloNome() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long ana = idDoUsuario("ana@exemplo.com");
        long alfa = criarCategoria(auth, "Alfa");
        long beta = criarCategoria(auth, "Beta");
        long gama = criarCategoria(auth, "Gama");
        long a = criarTarefa(auth, "A", alfa, null, null);
        long b = criarTarefa(auth, "B", beta, null, null);
        long g = criarTarefa(auth, "G", gama, null, null);
        // Todas com 1 concluída; Gama tem mais foco; Alfa e Beta empatam também no foco
        concluidaEm(a, sp(2026, 11, 24, 10, 0));
        concluidaEm(b, sp(2026, 11, 24, 11, 0));
        concluidaEm(g, sp(2026, 11, 24, 12, 0));
        sessaoFechada(ana, g, sp(2026, 11, 24, 8, 0), sp(2026, 11, 24, 9, 0));

        relatorio(auth, "WEEK", null)
                .andExpect(jsonPath("$.categories[0].name").value("Gama"))
                .andExpect(jsonPath("$.categories[1].name").value("Alfa"))
                .andExpect(jsonPath("$.categories[2].name").value("Beta"));
    }

    @Test
    void naoDeveContarDadosDeOutroUsuario() throws Exception {
        cenarioDaAna();
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");

        relatorio(bruno, "MONTH", null)
                .andExpect(jsonPath("$.summary.tasksCompleted").value(0))
                .andExpect(jsonPath("$.summary.focusSeconds").value(0))
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void deveRetornar400QuandoOPeriodoFaltaOuEInvalido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        relatorio(auth, "DAY", null).andExpect(status().isBadRequest());
        relatorio(auth, "WEEK", "25/11/2026").andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/reports").param("period", "WEEK")).andExpect(status().isUnauthorized());
    }
}
