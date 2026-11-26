package com.myck.productivity.dashboard;

import com.myck.productivity.AbstractFixedClockIntegrationTest;
import com.myck.productivity.dashboard.dto.CategoryStat;
import com.myck.productivity.dashboard.repository.DashboardQueries;
import com.myck.productivity.dashboard.repository.DashboardQueries.PlannedCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Testes de repositório das consultas agregadas do dashboard, direto contra o PostgreSQL real (sem passar
 * pela API). Cada teste fixa um caso de borda das regras de tempo e de fuso. "Agora" é sempre
 * {@link #AGORA}: quarta-feira, 25/11/2026, 15h em São Paulo.
 */
class DashboardQueriesIT extends AbstractFixedClockIntegrationTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final LocalDate SEG = LocalDate.of(2026, 11, 23);
    private static final LocalDate TER = LocalDate.of(2026, 11, 24);
    private static final LocalDate QUA = LocalDate.of(2026, 11, 25);

    @Autowired
    private DashboardQueries queries;

    private String auth;
    private long ana;
    private long estudos;
    private long tarefa;

    @BeforeEach
    void criarUsuarioETarefa() throws Exception {
        auth = novoUsuario("Ana", "ana@exemplo.com");
        ana = idDoUsuario("ana@exemplo.com");
        estudos = criarCategoria(auth, "Estudos");
        tarefa = criarTarefa(auth, "Estudar", estudos, null, null);
    }

    // Os mapas devolvidos não têm ordem (quem ordena os dias é o mapper); por isso as asserções usam containsOnly
    private Map<LocalDate, Long> focoPorDia(LocalDate de, LocalDate ate, ZoneId fuso) {
        return queries.focusSecondsByDay(ana, de, ate, fuso, AGORA);
    }

    // ---------- foco por dia: dias sem sessão (regressão do LEAST/GREATEST com nulos) ----------

    @Test
    void diaSemSessaoEntreDiasComSessaoDeveTerFocoZero() {
        // Regressão: LEAST/GREATEST ignoram nulos e, no LEFT JOIN, o dia vazio aparecia com 24h de foco
        sessaoFechada(ana, tarefa, sp(2026, 11, 23, 10, 0), sp(2026, 11, 23, 11, 0));
        sessaoFechada(ana, tarefa, sp(2026, 11, 25, 10, 0), sp(2026, 11, 25, 11, 0));

        assertThat(focoPorDia(SEG, QUA, SAO_PAULO))
                .containsOnly(entry(SEG, 3600L), entry(TER, 0L), entry(QUA, 3600L));
    }

    @Test
    void intervaloSemNenhumaSessaoDeveTrazerTodosOsDiasComZero() {
        Map<LocalDate, Long> foco = focoPorDia(SEG, SEG.plusDays(6), SAO_PAULO);

        assertThat(foco).hasSize(7);
        assertThat(foco.values()).containsOnly(0L);
    }

    @Test
    void sessaoQueCobreUmDiaInteiroContaVinteEQuatroHorasSoNesseDia() {
        // Contraste com o erro: aqui as 24h são legítimas, de uma sessão de verdade
        sessaoFechada(ana, tarefa, sp(2026, 11, 22, 23, 0), sp(2026, 11, 24, 1, 0));

        assertThat(focoPorDia(SEG, QUA, SAO_PAULO))
                .containsOnly(entry(SEG, 86400L), entry(TER, 3600L), entry(QUA, 0L));
    }

    // ---------- foco por dia: limites das sessões ----------

    @Test
    void sessaoQueAtravessaAMeiaNoiteDivideOTempoEntreOsDoisDias() {
        sessaoFechada(ana, tarefa, sp(2026, 11, 23, 23, 30), sp(2026, 11, 24, 0, 30));

        assertThat(focoPorDia(SEG, TER, SAO_PAULO)).containsOnly(entry(SEG, 1800L), entry(TER, 1800L));
    }

    @Test
    void sessaoQueComecaAntesDoIntervaloContaSoAParteDeDentro() {
        // Domingo 23h até segunda 1h, consultando só a segunda
        sessaoFechada(ana, tarefa, sp(2026, 11, 22, 23, 0), sp(2026, 11, 23, 1, 0));

        assertThat(focoPorDia(SEG, SEG, SAO_PAULO)).containsOnly(entry(SEG, 3600L));
    }

    @Test
    void sessaoForaDoIntervaloNaoConta() {
        sessaoFechada(ana, tarefa, sp(2026, 11, 20, 10, 0), sp(2026, 11, 20, 12, 0));

        assertThat(focoPorDia(SEG, QUA, SAO_PAULO).values()).containsOnly(0L);
    }

    @Test
    void variasSessoesNoMesmoDiaSaoSomadas() {
        sessaoFechada(ana, tarefa, sp(2026, 11, 24, 8, 0), sp(2026, 11, 24, 8, 25));
        sessaoFechada(ana, tarefa, sp(2026, 11, 24, 13, 0), sp(2026, 11, 24, 13, 45));
        sessaoFechada(ana, tarefa, sp(2026, 11, 24, 20, 10), sp(2026, 11, 24, 20, 20));

        assertThat(focoPorDia(TER, TER, SAO_PAULO)).containsOnly(entry(TER, 80 * 60L));
    }

    // ---------- foco por dia: sessões ainda abertas no banco ----------

    @Test
    void timerVencidoAindaAbertoContaSoAteOLimiteEscolhido() {
        // Timer de 30 min às 14h, ninguém pausou; às 15h conta 30 min, não 1h
        timerAberto(ana, tarefa, sp(2026, 11, 25, 14, 0), 30);

        assertThat(focoPorDia(QUA, QUA, SAO_PAULO)).containsOnly(entry(QUA, 1800L));
    }

    @Test
    void timerAindaDentroDoLimiteContaAteAgora() {
        // Timer de 60 min às 14h30: às 15h, 30 min decorridos
        timerAberto(ana, tarefa, sp(2026, 11, 25, 14, 30), 60);

        assertThat(focoPorDia(QUA, QUA, SAO_PAULO)).containsOnly(entry(QUA, 1800L));
    }

    @Test
    void cronometroLivreRodandoContaAteAgora() {
        cronometroLivreAberto(ana, tarefa, sp(2026, 11, 25, 14, 50));

        assertThat(focoPorDia(QUA, QUA, SAO_PAULO)).containsOnly(entry(QUA, 600L));
    }

    @Test
    void cronometroRodandoNaoContaEmDiasFuturos() {
        cronometroLivreAberto(ana, tarefa, sp(2026, 11, 25, 14, 50));

        assertThat(focoPorDia(QUA, QUA.plusDays(2), SAO_PAULO))
                .containsOnly(entry(QUA, 600L), entry(QUA.plusDays(1), 0L), entry(QUA.plusDays(2), 0L));
    }

    // ---------- foco por dia: usuário e fuso ----------

    @Test
    void sessaoDeOutroUsuarioNaoConta() throws Exception {
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long idBruno = idDoUsuario("bruno@exemplo.com");
        long tarefaBruno = criarTarefa(bruno, "Do Bruno", criarCategoria(bruno, "Trabalho"), null, null);
        sessaoFechada(idBruno, tarefaBruno, sp(2026, 11, 24, 10, 0), sp(2026, 11, 24, 12, 0));

        assertThat(focoPorDia(TER, TER, SAO_PAULO)).containsOnly(entry(TER, 0L));
    }

    @Test
    void mesmaSessaoCaiEmDiasDiferentesConformeOFuso() {
        // 22h–23h de 24/11 em São Paulo = 01h–02h de 25/11 em UTC
        sessaoFechada(ana, tarefa, sp(2026, 11, 24, 22, 0), sp(2026, 11, 24, 23, 0));

        assertThat(focoPorDia(TER, QUA, SAO_PAULO)).containsOnly(entry(TER, 3600L), entry(QUA, 0L));
        assertThat(focoPorDia(TER, QUA, UTC)).containsOnly(entry(TER, 0L), entry(QUA, 3600L));
    }

    // ---------- tarefas concluídas por dia ----------

    @Test
    void conclusaoPertoDaMeiaNoiteContaNoDiaDoFusoDoUsuario() throws Exception {
        // 23h30 de 24/11 em São Paulo = 02h30 de 25/11 em UTC
        long concluida = criarTarefa(auth, "Concluída", estudos, null, null);
        concluidaEm(concluida, sp(2026, 11, 24, 23, 30));

        assertThat(queries.completedTasksByDay(ana, TER, QUA, SAO_PAULO)).containsOnly(entry(TER, 1L));
        assertThat(queries.completedTasksByDay(ana, TER, QUA, UTC)).containsOnly(entry(QUA, 1L));
    }

    @Test
    void tarefaNaoConcluidaNaoContaComoConcluida() {
        // A tarefa do @BeforeEach está pendente
        assertThat(queries.completedTasksByDay(ana, SEG, QUA, SAO_PAULO)).isEmpty();
    }

    // ---------- categorias ----------

    @Test
    void categoriaSemAtividadeNoPeriodoFicaDeFora() throws Exception {
        criarCategoria(auth, "Lazer");
        sessaoFechada(ana, tarefa, sp(2026, 11, 24, 10, 0), sp(2026, 11, 24, 11, 0));

        assertThat(queries.categoryStats(ana, SEG, QUA, SAO_PAULO, AGORA))
                .extracting(CategoryStat::name).containsExactly("Estudos");
    }

    @Test
    void focoDaCategoriaContaSoAParteDaSessaoDentroDoPeriodo() {
        // Domingo 23h até segunda 1h: no período de segunda a quarta só entra 1h
        sessaoFechada(ana, tarefa, sp(2026, 11, 22, 23, 0), sp(2026, 11, 23, 1, 0));

        assertThat(queries.categoryStats(ana, SEG, QUA, SAO_PAULO, AGORA))
                .containsExactly(new CategoryStat(estudos, "Estudos", "#3B82F6", 0, 3600));
    }

    @Test
    void focoDaCategoriaUsaOFimEfetivoDoTimerVencido() {
        timerAberto(ana, tarefa, sp(2026, 11, 25, 14, 0), 15);

        assertThat(queries.categoryStats(ana, QUA, QUA, SAO_PAULO, AGORA))
                .extracting(CategoryStat::focusSeconds).containsExactly(900L);
    }

    // ---------- planejadas do dia ----------

    @Test
    void deveContarAsPlanejadasDoDiaEQuantasDelasEstaoConcluidasNaoImportaQuando() throws Exception {
        long planejada1 = criarTarefa(auth, "P1", estudos, null, "2026-11-25");
        criarTarefa(auth, "P2", estudos, null, "2026-11-25");
        criarTarefa(auth, "Outro dia", estudos, null, "2026-11-26");
        // Concluída no dia anterior ao planejado: ainda conta como planejada concluída
        concluidaEm(planejada1, sp(2026, 11, 24, 18, 0));

        assertThat(queries.plannedTasks(ana, QUA)).isEqualTo(new PlannedCount(2, 1));
    }

    @Test
    void diaSemNadaPlanejadoDeveDarZeroEZero() {
        assertThat(queries.plannedTasks(ana, QUA)).isEqualTo(new PlannedCount(0, 0));
    }
}
