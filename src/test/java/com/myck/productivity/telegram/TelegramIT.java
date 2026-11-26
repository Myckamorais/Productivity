package com.myck.productivity.telegram;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.AbstractFixedClockIntegrationTest;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.service.TimerExpiryNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bot do Telegram de ponta a ponta: as mensagens chegam pelo webhook como o Telegram as enviaria (JSON simulado)
 * e o resultado é conferido no banco e pela API. O cliente do Telegram é um mock: nenhum teste chama o Telegram
 * de verdade, e as respostas do bot são capturadas nele. "Hoje" é 25/11/2026 (relógio fixo).
 */
@TestPropertySource(properties = {
        "app.telegram.token=token-de-teste",
        "app.telegram.webhook-secret=segredo-de-teste",
        // Webhook: o polling não pode ligar nos testes (tentaria falar com o Telegram)
        "app.telegram.mode=webhook",
        // A verificação agendada não roda durante os testes; eles chamam o aviso diretamente
        "app.telegram.expiry-check-interval=PT24H"})
class TelegramIT extends AbstractFixedClockIntegrationTest {

    private static final long CHAT_ANA = 5550001L;
    private static final long CHAT_BRUNO = 5550002L;
    private static final String HOJE = "2026-11-25";

    @MockitoBean
    private TelegramClient telegram;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TimerExpiryNotifier notifier;

    private long proximoUpdate = 1;

    // ---------- helpers ----------

    /** Simula o Telegram entregando uma mensagem de texto de uma conversa privada ao webhook. */
    private ResultActions mensagem(long chatId, String texto) throws Exception {
        return mensagemComSegredo(chatId, texto, "private", "segredo-de-teste");
    }

    private ResultActions mensagemComSegredo(long chatId, String texto, String tipoChat, String segredo)
            throws Exception {
        String corpo = """
                {"update_id": %d,
                 "message": {"message_id": 1, "date": 1764093600, "text": %s,
                             "chat": {"id": %d, "type": "%s", "first_name": "Teste"},
                             "from": {"id": %d, "is_bot": false, "first_name": "Teste"}}}"""
                .formatted(proximoUpdate++, json(texto), chatId, tipoChat, chatId);
        var requisicao = post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (segredo != null) {
            requisicao.header("X-Telegram-Bot-Api-Secret-Token", segredo);
        }
        return mockMvc.perform(requisicao);
    }

    /** Última resposta do bot no chat informado. */
    private String ultimaResposta(long chatId) {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(telegram, atLeastOnce()).sendMessage(eq(chatId), texto.capture());
        return texto.getValue();
    }

    private String gerarCodigo(String auth) throws Exception {
        String resposta = mockMvc.perform(post("/telegram/link-code").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.code");
    }

    /** Vincula o chat à conta pelo fluxo real: código gerado no app e enviado ao bot com /start. */
    private void vincular(String auth, long chatId) throws Exception {
        mensagem(chatId, "/start " + gerarCodigo(auth)).andExpect(status().isOk());
    }

    private static String json(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ---------- vinculação ----------

    @Test
    void deveGerarCodigoValidoPorDezMinutosComLinkQueAbreOBot() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(post("/telegram/link-code").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("[A-Z2-9]{8}")))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-25T18:10:00Z"))
                .andExpect(jsonPath("$.deepLink").value(startsWith("https://t.me/tarefanoar_bot?start=")));
    }

    @Test
    void deveVincularOChatPeloCodigoEMostrarNoStatus() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.linkedAt").doesNotExist());

        vincular(auth, CHAT_ANA);

        assertThat(ultimaResposta(CHAT_ANA)).contains("Conta vinculada");
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedAt").value("2026-11-25T18:00:00Z"));
    }

    @Test
    void deveAceitarOCodigoPeloComandoVincular() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mensagem(CHAT_ANA, "/vincular " + gerarCodigo(auth).toLowerCase());

        assertThat(ultimaResposta(CHAT_ANA)).contains("Conta vinculada");
    }

    @Test
    void codigoDeveServirUmaVezSo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        String codigo = gerarCodigo(auth);
        mensagem(CHAT_ANA, "/start " + codigo);

        mensagem(CHAT_BRUNO, "/start " + codigo);

        assertThat(ultimaResposta(CHAT_BRUNO)).contains("inválido");
    }

    @Test
    void deveRecusarCodigoVencido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        String codigo = gerarCodigo(auth);
        jdbc.update("UPDATE telegram_link_codes SET expires_at = ? WHERE code = ?",
                OffsetDateTime.ofInstant(AGORA.minusSeconds(1), ZoneOffset.UTC), codigo);

        mensagem(CHAT_ANA, "/start " + codigo);

        assertThat(ultimaResposta(CHAT_ANA)).contains("venceu");
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.linked").value(false));
    }

    @Test
    void novoCodigoDeveInvalidarOAnterior() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        String primeiro = gerarCodigo(auth);
        gerarCodigo(auth);

        mensagem(CHAT_ANA, "/start " + primeiro);

        assertThat(ultimaResposta(CHAT_ANA)).contains("inválido");
    }

    @Test
    void devePassarOChatParaAContaQueVinculouPorUltimo() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        vincular(ana, CHAT_ANA);

        vincular(bruno, CHAT_ANA);

        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.linked").value(false));
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.linked").value(true));
    }

    @Test
    void deveDesvincularPeloAppEPeloBot() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);

        mockMvc.perform(delete("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.linked").value(false));

        // Pelo bot
        vincular(auth, CHAT_ANA);
        mensagem(CHAT_ANA, "/desvincular");
        assertThat(ultimaResposta(CHAT_ANA)).contains("desvinculado");
        mockMvc.perform(get("/telegram/link").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.linked").value(false));
    }

    @Test
    void deveExigirLoginNasRotasDeVinculacao() throws Exception {
        mockMvc.perform(post("/telegram/link-code")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/telegram/link")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/telegram/link")).andExpect(status().isUnauthorized());
    }

    // ---------- segurança do webhook ----------

    @Test
    void deveRecusarWebhookSemSegredoOuComSegredoErradoSemProcessarNada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);

        mensagemComSegredo(CHAT_ANA, "Tarefa falsa", "private", null).andExpect(status().isUnauthorized());
        mensagemComSegredo(CHAT_ANA, "Tarefa falsa", "private", "chute").andExpect(status().isUnauthorized());

        assertThat(taskRepository.count()).isZero();
    }

    // ---------- tarefas por texto (o "pronto quando" da fase) ----------

    @Test
    void deveCriarAsTarefasDaListaEnviadaPeloBot() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);

        mensagem(CHAT_ANA, "Tarefas de hoje:\n- Estudar Spring\n- Academia\n3. Ler artigo").andExpect(status().isOk());

        assertThat(ultimaResposta(CHAT_ANA)).contains("4 tarefas criadas");
        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].title", hasItem("Estudar Spring")))
                .andExpect(jsonPath("$.content[*].title", hasItem("Ler artigo")))
                .andExpect(jsonPath("$.content[0].plannedDate").value(HOJE))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        // A categoria "Telegram" foi criada e recebeu as tarefas
        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Telegram"));
    }

    @Test
    void deveUsarSempreAMesmaCategoriaMesmoDepoisDeRenomeada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        mensagem(CHAT_ANA, "Primeira");
        long categoria = JsonPath.<Integer>read(mockMvc.perform(get("/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth)).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        mockMvc.perform(put("/categories/{id}", categoria)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Caixa de entrada\", \"color\": \"#229ED9\"}"))
                .andExpect(status().isOk());

        mensagem(CHAT_ANA, "Segunda");

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Caixa de entrada"));
        mockMvc.perform(get("/tasks").param("categoryId", String.valueOf(categoria))
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void naoDeveCriarTarefasParaChatNaoVinculado() throws Exception {
        novoUsuario("Ana", "ana@exemplo.com");

        mensagem(CHAT_ANA, "Estudar").andExpect(status().isOk());

        assertThat(ultimaResposta(CHAT_ANA)).contains("/vincular");
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void deveIgnorarMensagensDeGrupo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);

        mensagemComSegredo(-100123L, "Estudar", "group", "segredo-de-teste").andExpect(status().isOk());

        assertThat(taskRepository.count()).isZero();
        verify(telegram, never()).sendMessage(eq(-100123L), anyString());
    }

    @Test
    void cadaChatCriaTarefasSoParaASuaConta() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        vincular(ana, CHAT_ANA);
        vincular(bruno, CHAT_BRUNO);

        mensagem(CHAT_BRUNO, "Do Bruno");

        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/tasks").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Do Bruno"));
    }

    // ---------- /hoje e /concluir ----------

    @Test
    void deveListarAsTarefasDeHojeEConcluirPeloNumero() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        mensagem(CHAT_ANA, "Estudar\nAcademia\nLer");
        // Tarefa de amanhã não entra no /hoje
        criarTarefa(auth, "Amanhã", criarCategoria(auth, "Outra"), null, "2026-11-26");

        mensagem(CHAT_ANA, "/hoje");
        assertThat(ultimaResposta(CHAT_ANA))
                .contains("Tarefas de hoje (25/11)")
                .contains("1. ⬜ Estudar").contains("2. ⬜ Academia").contains("3. ⬜ Ler")
                .doesNotContain("Amanhã");

        mensagem(CHAT_ANA, "/concluir 2");
        assertThat(ultimaResposta(CHAT_ANA)).contains("✅ 2. Academia");

        // A numeração não muda depois de concluir: a 2 continua sendo Academia, agora marcada
        mensagem(CHAT_ANA, "/hoje");
        assertThat(ultimaResposta(CHAT_ANA)).contains("2. ✅ Academia").contains("3. ⬜ Ler");
        mockMvc.perform(get("/tasks").param("status", "COMPLETED").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Academia"))
                .andExpect(jsonPath("$.content[0].completedAt").value("2026-11-25T18:00:00Z"));
    }

    @Test
    void concluirPeloBotDeveFecharOCronometroRodando() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        mensagem(CHAT_ANA, "Estudar");
        long tarefa = taskRepository.findAll().getFirst().getId();
        mockMvc.perform(post("/tasks/{id}/start", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        mensagem(CHAT_ANA, "/concluir 1");

        mockMvc.perform(get("/tasks/{id}", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.timerState").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_sessions WHERE ended_at IS NULL", Integer.class))
                .isZero();
    }

    // ---------- aviso de fim do timer ----------

    private void sessaoDeTimer(long userId, long taskId, Instant inicio, int minutos, Instant fim) {
        jdbc.update("""
                INSERT INTO task_sessions (user_id, task_id, started_at, ended_at, planned_minutes, expires_at, block_id)
                VALUES (?, ?, ?, ?, ?, ?, gen_random_uuid())""",
                userId, taskId, OffsetDateTime.ofInstant(inicio, ZoneOffset.UTC),
                fim == null ? null : OffsetDateTime.ofInstant(fim, ZoneOffset.UTC), minutos,
                OffsetDateTime.ofInstant(inicio.plusSeconds(minutos * 60L), ZoneOffset.UTC));
    }

    @Test
    void deveAvisarUmaVezSoQuandoOTempoDoTimerAcaba() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        long tarefa = criarTarefa(auth, "Estudar Spring", criarCategoria(auth, "Estudos"), null, HOJE);
        // Timer de 30 min às 14h (15h agora): acabou às 14h30 e ninguém pausou
        timerAberto(idDoUsuario("ana@exemplo.com"), tarefa, sp(2026, 11, 25, 14, 0), 30);

        assertThat(notifier.notifyExpiredTimers()).isEqualTo(1);
        assertThat(ultimaResposta(CHAT_ANA)).contains("Acabaram os 30 min de \"Estudar Spring\"");

        // Na próxima verificação, não avisa de novo
        assertThat(notifier.notifyExpiredTimers()).isZero();
    }

    @Test
    void deveAvisarTambemQuandoASessaoJaFoiFechadaNoLimite() throws Exception {
        // A aplicação fecha a sessão vencida no limite na próxima ação do usuário; o aviso ainda é devido
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        long tarefa = criarTarefa(auth, "Estudar", criarCategoria(auth, "Estudos"), null, HOJE);
        sessaoDeTimer(idDoUsuario("ana@exemplo.com"), tarefa, sp(2026, 11, 25, 14, 0), 15, sp(2026, 11, 25, 14, 15));

        assertThat(notifier.notifyExpiredTimers()).isEqualTo(1);
    }

    @Test
    void naoDeveAvisarQuandoOTimerFoiPausadoAntesDoFim() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        long tarefa = criarTarefa(auth, "Estudar", criarCategoria(auth, "Estudos"), null, HOJE);
        // 30 min escolhidos, pausado com 20
        sessaoDeTimer(idDoUsuario("ana@exemplo.com"), tarefa, sp(2026, 11, 25, 14, 0), 30, sp(2026, 11, 25, 14, 20));

        assertThat(notifier.notifyExpiredTimers()).isZero();
    }

    @Test
    void naoDeveAvisarTimerAindaRodandoNemSemTelegramNemMuitoAntigo() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        vincular(ana, CHAT_ANA);
        long tarefaAna = criarTarefa(ana, "Rodando", criarCategoria(ana, "Estudos"), null, HOJE);
        long tarefaAntiga = criarTarefa(ana, "Antiga", criarCategoria(ana, "Outra"), null, HOJE);
        // Ainda rodando: 30 min a partir das 14h50
        timerAberto(idDoUsuario("ana@exemplo.com"), tarefaAna, sp(2026, 11, 25, 14, 50), 30);
        // Venceu às 12h30 (há 2h30): fora da janela de 1h, provavelmente a aplicação estava desligada
        sessaoDeTimer(idDoUsuario("ana@exemplo.com"), tarefaAntiga, sp(2026, 11, 25, 12, 0), 30,
                sp(2026, 11, 25, 12, 30));
        // Bruno não tem Telegram vinculado
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long tarefaBruno = criarTarefa(bruno, "Do Bruno", criarCategoria(bruno, "Estudos"), null, HOJE);
        timerAberto(idDoUsuario("bruno@exemplo.com"), tarefaBruno, sp(2026, 11, 25, 14, 0), 15);

        assertThat(notifier.notifyExpiredTimers()).isZero();
        verify(telegram, never()).sendMessage(anyLong(), contains("Acabaram"));
    }

    @Test
    void deveTentarDeNovoQuandoOEnvioDoAvisoFalha() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        vincular(auth, CHAT_ANA);
        long tarefa = criarTarefa(auth, "Estudar", criarCategoria(auth, "Estudos"), null, HOJE);
        timerAberto(idDoUsuario("ana@exemplo.com"), tarefa, sp(2026, 11, 25, 14, 0), 30);
        doThrow(new TelegramClient.TelegramException("fora do ar"))
                .when(telegram).sendMessage(eq(CHAT_ANA), anyString());

        assertThat(notifier.notifyExpiredTimers()).isZero();

        // O Telegram volta: a próxima verificação avisa
        reset(telegram);
        assertThat(notifier.notifyExpiredTimers()).isEqualTo(1);
    }
}
