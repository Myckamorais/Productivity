package com.myck.productivity.dashboard;

import com.myck.productivity.AbstractFixedClockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /dashboard/today com uma massa de dados conhecida; "hoje" é quarta, 25/11/2026, 15h em São Paulo. */
class DashboardTodayIT extends AbstractFixedClockIntegrationTest {

    private static final String HOJE = "2026-11-25";
    private static final String AMANHA = "2026-11-26";

    /**
     * Monta o cenário da Ana:
     * <pre>
     * Tarefas (data planejada)        Conclusão
     *   T1 Estudos, meta 1, hoje      hoje 15h (pela API)
     *   T2 Estudos, meta 1, hoje      hoje 15h (pela API)
     *   T3 Trabalho, hoje             ontem 20h
     *   T4 Trabalho, hoje             -
     *   T5 Trabalho, amanhã           -
     *   T6 Trabalho, sem data         hoje 00h10
     * Sessões (horário de São Paulo)
     *   T1  09:00–10:00 de hoje                      → 1h hoje
     *   T3  23:30 de ontem até 00:30 de hoje         → 30 min hoje (e 30 min ontem)
     *   T4  timer de 30 min às 14:00, nunca pausado  → 30 min hoje (para no limite, não até as 15h)
     * Metas: 1 "Aprender Java" (em andamento, T1 e T2), 2 "Antiga" (concluída), 3 "Vazia" (em andamento)
     * </pre>
     */
    private String cenarioDaAna() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long ana = idDoUsuario("ana@exemplo.com");
        long estudos = criarCategoria(auth, "Estudos");
        long trabalho = criarCategoria(auth, "Trabalho");
        long meta1 = criarMeta(auth, "Aprender Java");
        mockMvc.perform(post("/goals")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Antiga\", \"status\": \"COMPLETED\"}"))
                .andExpect(status().isCreated());
        criarMeta(auth, "Vazia");

        long t1 = criarTarefa(auth, "T1", estudos, meta1, HOJE);
        long t2 = criarTarefa(auth, "T2", estudos, meta1, HOJE);
        long t3 = criarTarefa(auth, "T3", trabalho, null, HOJE);
        long t4 = criarTarefa(auth, "T4", trabalho, null, HOJE);
        criarTarefa(auth, "T5", trabalho, null, AMANHA);
        long t6 = criarTarefa(auth, "T6", trabalho, null, null);

        mockMvc.perform(patch("/tasks/{id}/complete", t1).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tasks/{id}/complete", t2).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        concluidaEm(t3, sp(2026, 11, 24, 20, 0));
        concluidaEm(t6, sp(2026, 11, 25, 0, 10));

        sessaoFechada(ana, t1, sp(2026, 11, 25, 9, 0), sp(2026, 11, 25, 10, 0));
        sessaoFechada(ana, t3, sp(2026, 11, 24, 23, 30), sp(2026, 11, 25, 0, 30));
        timerAberto(ana, t4, sp(2026, 11, 25, 14, 0), 30);
        return auth;
    }

    @Test
    void deveMontarOResumoDeHojeComOsNumerosConferidos() throws Exception {
        String auth = cenarioDaAna();
        configurar(auth, "America/Sao_Paulo", 240);

        mockMvc.perform(get("/dashboard/today").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(HOJE))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                // Concluídas hoje: T1, T2 e T6 (T3 foi ontem)
                .andExpect(jsonPath("$.tasksCompleted").value(3))
                // Planejadas para hoje: T1 a T4; concluídas entre elas: T1, T2 e T3 → 75%
                .andExpect(jsonPath("$.plannedTasks").value(4))
                .andExpect(jsonPath("$.plannedTasksCompleted").value(3))
                .andExpect(jsonPath("$.completionPercent").value(75))
                // Foco de hoje: 1h + 30 min (só a parte depois da meia-noite) + 30 min (timer parado no limite) = 2h
                .andExpect(jsonPath("$.focusSeconds").value(7200))
                // 2h de 4h de meta = 50%
                .andExpect(jsonPath("$.focusGoalMinutes").value(240))
                .andExpect(jsonPath("$.focusPercent").value(50))
                // Só as metas em andamento, cada uma com o progresso
                .andExpect(jsonPath("$.goalsInProgress.length()").value(2))
                .andExpect(jsonPath("$.goalsInProgress[*].title", containsInAnyOrder("Aprender Java", "Vazia")))
                .andExpect(jsonPath("$.goalsInProgress[?(@.title == 'Aprender Java')].progressPercent").value(100))
                .andExpect(jsonPath("$.goalsInProgress[?(@.title == 'Vazia')].progressPercent").value(0));
    }

    @Test
    void deveMudarOsLimitesDoDiaConformeOFusoDoUsuario() throws Exception {
        // Mesmos dados, mas o usuário está em UTC: às 18h UTC ainda é dia 25 lá também, porém o "dia" começa 3h depois
        String auth = cenarioDaAna();
        configurar(auth, "UTC", 240);

        mockMvc.perform(get("/dashboard/today").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.date").value(HOJE))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                // A sessão das 23:30–00:30 de São Paulo é 02:30–03:30 UTC: agora conta inteira em hoje
                // 1h + 1h + 30 min = 2h30 → 62,5% de 4h, arredondado para 63%
                .andExpect(jsonPath("$.focusSeconds").value(9000))
                .andExpect(jsonPath("$.focusPercent").value(63))
                // T3 (ontem 20h SP = ontem 23h UTC) continua ontem; T6 (00:10 SP = 03:10 UTC) continua hoje
                .andExpect(jsonPath("$.tasksCompleted").value(3));
    }

    @Test
    void naoDeveCalcularRendimentosSemPlanejamentoESemMetaDiaria() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/dashboard/today").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksCompleted").value(0))
                .andExpect(jsonPath("$.plannedTasks").value(0))
                .andExpect(jsonPath("$.completionPercent").doesNotExist())
                .andExpect(jsonPath("$.focusSeconds").value(0))
                .andExpect(jsonPath("$.focusGoalMinutes").doesNotExist())
                .andExpect(jsonPath("$.focusPercent").doesNotExist())
                .andExpect(jsonPath("$.goalsInProgress.length()").value(0));
    }

    @Test
    void deveContarOCronometroLivreRodandoAteAgora() throws Exception {
        // Cronômetro livre iniciado às 14:50 e ainda rodando às 15:00: 10 minutos
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = criarTarefa(auth, "Rodando", criarCategoria(auth, "Estudos"), null, HOJE);
        cronometroLivreAberto(idDoUsuario("ana@exemplo.com"), tarefa, sp(2026, 11, 25, 14, 50));

        mockMvc.perform(get("/dashboard/today").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.focusSeconds").value(600));
    }

    @Test
    void naoDeveContarDadosDeOutroUsuario() throws Exception {
        cenarioDaAna();
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");

        mockMvc.perform(get("/dashboard/today").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.tasksCompleted").value(0))
                .andExpect(jsonPath("$.plannedTasks").value(0))
                .andExpect(jsonPath("$.focusSeconds").value(0))
                .andExpect(jsonPath("$.goalsInProgress.length()").value(0));
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/dashboard/today")).andExpect(status().isUnauthorized());
    }
}
