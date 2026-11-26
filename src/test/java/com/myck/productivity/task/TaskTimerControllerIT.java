package com.myck.productivity.task;

import com.myck.productivity.AbstractIntegrationTest;
import com.myck.productivity.task.entity.TaskSession;
import com.myck.productivity.task.repository.TaskRepository;
import com.myck.productivity.task.repository.TaskSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskTimerControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TaskSessionRepository sessionRepository;
    @Autowired
    private TaskRepository taskRepository;

    // ---------- ações ----------

    private ResultActions acao(String auth, String caminho, long tarefa) throws Exception {
        return mockMvc.perform(post("/tasks/{id}/" + caminho, tarefa).header(HttpHeaders.AUTHORIZATION, auth));
    }

    private ResultActions acaoComDuracao(String auth, String caminho, long tarefa, int minutos) throws Exception {
        return mockMvc.perform(post("/tasks/{id}/" + caminho, tarefa)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"durationMinutes\": " + minutos + "}"));
    }

    private ResultActions timer(String auth, long tarefa) throws Exception {
        return mockMvc.perform(get("/tasks/{id}/timer", tarefa).header(HttpHeaders.AUTHORIZATION, auth));
    }

    // Cria a tarefa numa categoria nova do próprio usuário (nome único, pois o nome não pode repetir por usuário)
    private long novaTarefa(String auth, String titulo) throws Exception {
        long categoria = criarCategoria(auth, "Categoria " + System.nanoTime());
        return criarTarefa(auth, titulo, categoria, null, null);
    }

    // Recua o início da sessão aberta da tarefa, simulando que ela começou há N minutos (sem esperar de verdade)
    private void recuarSessaoAberta(long tarefa, int minutos) {
        jdbc.update("""
                update task_sessions
                   set started_at = started_at - make_interval(mins => ?),
                       expires_at = expires_at - make_interval(mins => ?)
                 where task_id = ? and ended_at is null""", minutos, minutos, tarefa);
    }

    private int sessoesAbertas(long tarefa) {
        return jdbc.queryForObject("select count(*) from task_sessions where task_id = ? and ended_at is null",
                Integer.class, tarefa);
    }

    // ---------- start ----------

    @Test
    void deveIniciarCronometroLivreQuandoNaoHaCorpo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        acao(auth, "start", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(tarefa))
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.activeSession.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.activeSession.plannedMinutes").doesNotExist())
                .andExpect(jsonPath("$.activeSession.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.activeSession.remainingSeconds").doesNotExist());

        mockMvc.perform(get("/tasks/{id}", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void deveIniciarCronometroLivreQuandoOCorpoEstaVazio() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        mockMvc.perform(post("/tasks/{id}/start", tarefa)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSession.plannedMinutes").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 45, 60})
    void deveIniciarComADuracaoEscolhidaEInformarOTempoRestante(int minutos) throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        acaoComDuracao(auth, "start", tarefa, minutos)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.activeSession.plannedMinutes").value(minutos))
                .andExpect(jsonPath("$.activeSession.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.activeSession.remainingSeconds",
                        allOf(greaterThanOrEqualTo(minutos * 60 - 10), lessThanOrEqualTo(minutos * 60))));
    }

    @Test
    void deveRetornar400ComODetalheQuandoADuracaoNaoEPermitida() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        acaoComDuracao(auth, "start", tarefa, 20)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("durationMinutes"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("A duração deve ser 15, 30, 45 ou 60 minutos."));

        // Nada foi iniciado
        timer(auth, tarefa).andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void deveRetornar400QuandoADuracaoNaoEUmNumero() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        mockMvc.perform(post("/tasks/{id}/start", tarefa)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\": \"meia hora\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("durationMinutes"));
    }

    // ---------- fluxo completo: start, pause, resume, finish ----------

    @Test
    void deveSomarAsSessoesNoFluxoIniciarPausarRetomarFinalizar() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        // Começa e "passam" 10 minutos
        acao(auth, "start", tarefa).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 10);

        // Pausa: 10 minutos contados, nenhuma sessão rodando
        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.taskStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeSession").doesNotExist())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].endedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalSeconds", allOf(greaterThanOrEqualTo(600), lessThanOrEqualTo(605))));

        // Retoma e "passam" mais 5 minutos
        acao(auth, "resume", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.sessions.length()").value(2));
        recuarSessaoAberta(tarefa, 5);

        // Finaliza: total = 10 + 5 minutos, tarefa concluída
        acao(auth, "finish", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.taskStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.activeSession").doesNotExist())
                .andExpect(jsonPath("$.totalSeconds", allOf(greaterThanOrEqualTo(900), lessThanOrEqualTo(910))));

        mockMvc.perform(get("/tasks/{id}", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertThat(sessoesAbertas(tarefa)).isZero();
    }

    @Test
    void deveRetomarComUmaNovaDuracaoEscolhida() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        acao(auth, "pause", tarefa).andExpect(status().isOk());

        acaoComDuracao(auth, "resume", tarefa, 45)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSession.plannedMinutes").value(45));
    }

    @Test
    void deveFinalizarUmaTarefaPausada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 20);
        acao(auth, "pause", tarefa).andExpect(status().isOk());

        acao(auth, "finish", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.totalSeconds", allOf(greaterThanOrEqualTo(1200), lessThanOrEqualTo(1205))));
    }

    @Test
    void devePausarDeNovoSemErroQuandoJaEstaPausada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        acao(auth, "pause", tarefa).andExpect(status().isOk());

        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.sessions.length()").value(1));
    }

    // ---------- fim do tempo escolhido ----------

    @Test
    void deveParaDeContarNoLimiteQuandoOTempoEscolhidoAcaba() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 15).andExpect(status().isOk());

        // Passam 20 minutos sem ninguém pausar: o tempo escolhido (15) acabou há 5
        recuarSessaoAberta(tarefa, 20);

        // A consulta já mostra a sessão encerrada no limite, com 15 minutos
        timer(auth, tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.activeSession").doesNotExist())
                .andExpect(jsonPath("$.totalSeconds").value(900))
                .andExpect(jsonPath("$.remainingSeconds").value(0))
                .andExpect(jsonPath("$.sessions[0].durationSeconds").value(900))
                .andExpect(jsonPath("$.sessions[0].endedAt").isNotEmpty());

        // Consultar não altera o banco: a sessão ainda consta aberta lá
        assertThat(sessoesAbertas(tarefa)).isEqualTo(1);
    }

    @Test
    void deveGravarOFimNoLimiteQuandoOUsuarioPausaDepoisDoFimDoTempo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 15).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 20);

        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.totalSeconds").value(900));

        // No banco, o fim foi gravado exatamente 15 minutos depois do início, e não "agora"
        Double segundos = jdbc.queryForObject(
                "select extract(epoch from (ended_at - started_at)) from task_sessions where task_id = ?",
                Double.class, tarefa);
        assertThat(segundos).isEqualTo(900.0);
    }

    @Test
    void deveFinalizarContandoSoOLimiteEscolhido() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 30).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 50);

        acao(auth, "finish", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.totalSeconds").value(1800));
    }

    @Test
    void deveExigirNovaDuracaoParaRetomarDepoisQueOTempoEscolhidoAcabouSozinho() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 15).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 20);

        // Sem duração não há o que continuar: o tempo escolhido acabou
        acao(auth, "resume", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("durationMinutes")));

        // Informando uma nova duração, começa um bloco novo
        acaoComDuracao(auth, "resume", tarefa, 30)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.activeSession.plannedMinutes").value(30))
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].durationSeconds").value(900));
    }

    // ---------- a pausa preserva o tempo restante ----------

    @Test
    void devePreservarORestanteNaPausaEContinuarDeOndeParouAoRetomar() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        // Timer de 15 min; passam 5 e o usuário pausa: sobram 10
        acaoComDuracao(auth, "start", tarefa, 15).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 5);
        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.remainingSeconds", allOf(greaterThanOrEqualTo(595), lessThanOrEqualTo(600))));

        // A consulta durante a pausa mostra os mesmos 10 minutos parados
        timer(auth, tarefa)
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.remainingSeconds", allOf(greaterThanOrEqualTo(595), lessThanOrEqualTo(600))));

        // Retoma sem informar duração: continua o mesmo timer de 15 com os ~10 minutos que sobraram
        acao(auth, "resume", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.activeSession.plannedMinutes").value(15))
                .andExpect(jsonPath("$.activeSession.remainingSeconds",
                        allOf(greaterThanOrEqualTo(590), lessThanOrEqualTo(600))))
                .andExpect(jsonPath("$.remainingSeconds", allOf(greaterThanOrEqualTo(590), lessThanOrEqualTo(600))));
    }

    @Test
    void deveSomarVariasPausasAteOTempoEscolhidoAcabar() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        // 15 min: usa 5, pausa, retoma, usa mais 6, pausa: sobram ~4
        acaoComDuracao(auth, "start", tarefa, 15).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 5);
        acao(auth, "pause", tarefa).andExpect(status().isOk());
        acao(auth, "resume", tarefa).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 6);
        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.remainingSeconds", allOf(greaterThanOrEqualTo(230), lessThanOrEqualTo(240))))
                .andExpect(jsonPath("$.totalSeconds", allOf(greaterThanOrEqualTo(660), lessThanOrEqualTo(670))));

        // Retoma de novo e deixa passar muito além do que sobrava: a sessão para no limite do bloco
        acao(auth, "resume", tarefa).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 30);

        // No total foram gastos exatamente os 15 minutos escolhidos, nem um segundo a mais
        timer(auth, tarefa)
                .andExpect(jsonPath("$.state").value("PAUSED"))
                .andExpect(jsonPath("$.activeSession").doesNotExist())
                .andExpect(jsonPath("$.totalSeconds").value(900))
                .andExpect(jsonPath("$.remainingSeconds").value(0));

        // E agora só dá para retomar escolhendo uma nova duração
        acao(auth, "resume", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("durationMinutes")));
    }

    @Test
    void deveDescartarORestanteQuandoORetomarInformaUmaNovaDuracao() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 30).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 10);
        acao(auth, "pause", tarefa).andExpect(status().isOk());

        // Havia ~20 min de sobra; escolher 15 começa um bloco novo de 15 inteiros
        acaoComDuracao(auth, "resume", tarefa, 15)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSession.plannedMinutes").value(15))
                .andExpect(jsonPath("$.remainingSeconds", allOf(greaterThanOrEqualTo(890), lessThanOrEqualTo(900))));
    }

    @Test
    void deveContinuarLivreAoRetomarUmCronometroLivre() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        acao(auth, "pause", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").doesNotExist());

        acao(auth, "resume", tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.activeSession.plannedMinutes").doesNotExist())
                .andExpect(jsonPath("$.remainingSeconds").doesNotExist());
    }

    @Test
    void deveManterOMesmoBlocoNoBancoAoRetomarSemDuracaoEMudarAoInformarUma() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acaoComDuracao(auth, "start", tarefa, 30).andExpect(status().isOk());
        acao(auth, "pause", tarefa).andExpect(status().isOk());
        acao(auth, "resume", tarefa).andExpect(status().isOk());
        acao(auth, "pause", tarefa).andExpect(status().isOk());
        acaoComDuracao(auth, "resume", tarefa, 45).andExpect(status().isOk());

        // 3 sessões: as duas primeiras no mesmo bloco, a terceira em outro
        var blocos = jdbc.queryForList("select block_id::text from task_sessions where task_id = ? order by started_at",
                String.class, tarefa);
        assertThat(blocos).hasSize(3).doesNotContainNull();
        assertThat(blocos.get(0)).isEqualTo(blocos.get(1)).isNotEqualTo(blocos.get(2));
    }

    @Test
    void devePermitirIniciarOutraTarefaDepoisQueOTempoDaAnteriorAcabouSozinho() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long primeira = novaTarefa(auth, "Primeira");
        long segunda = novaTarefa(auth, "Segunda");
        acaoComDuracao(auth, "start", primeira, 15).andExpect(status().isOk());
        recuarSessaoAberta(primeira, 20);

        // A primeira venceu e ninguém a pausou: não deve bloquear o início da segunda
        acao(auth, "start", segunda).andExpect(status().isOk());

        assertThat(sessoesAbertas(primeira)).isZero();
        assertThat(sessoesAbertas(segunda)).isEqualTo(1);
    }

    // ---------- só um cronômetro ativo por usuário ----------

    @Test
    void deveRecusarIniciarOutraTarefaEnquantoHaCronometroAtivoELiberarAoPausar() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long primeira = novaTarefa(auth, "Primeira");
        long segunda = novaTarefa(auth, "Segunda");
        acao(auth, "start", primeira).andExpect(status().isOk());

        acao(auth, "start", segunda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(String.valueOf(primeira))));
        // A segunda continua intocada
        timer(auth, segunda).andExpect(jsonPath("$.state").value("IDLE"));

        acao(auth, "pause", primeira).andExpect(status().isOk());
        acao(auth, "start", segunda).andExpect(status().isOk());
    }

    @Test
    void deveRecusarRetomarEnquantoOutraTarefaTemCronometroAtivo() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long primeira = novaTarefa(auth, "Primeira");
        long segunda = novaTarefa(auth, "Segunda");
        acao(auth, "start", primeira).andExpect(status().isOk());
        acao(auth, "pause", primeira).andExpect(status().isOk());
        acao(auth, "start", segunda).andExpect(status().isOk());

        acao(auth, "resume", primeira).andExpect(status().isConflict());
    }

    @Test
    void usuariosDiferentesPodemCronometrarAoMesmoTempo() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long tarefaDaAna = novaTarefa(ana, "Da Ana");
        long categoriaDoBruno = criarCategoria(bruno, "Trabalho");
        long tarefaDoBruno = criarTarefa(bruno, "Do Bruno", categoriaDoBruno, null, null);

        acao(ana, "start", tarefaDaAna).andExpect(status().isOk());
        acao(bruno, "start", tarefaDoBruno).andExpect(status().isOk());
    }

    @Test
    void deveRejeitarNoBancoUmaSegundaSessaoAbertaDoMesmoUsuario() throws Exception {
        // Given: duas tarefas do mesmo usuário e uma sessão aberta na primeira
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long primeira = novaTarefa(auth, "Primeira");
        long segunda = novaTarefa(auth, "Segunda");
        long userId = userRepository.findByEmail("ana@exemplo.com").orElseThrow().getId();
        sessionRepository.saveAndFlush(new TaskSession(userId, primeira, Instant.now(), null));

        // When / Then: o índice único parcial barra a segunda sessão aberta, mesmo sem passar pela aplicação
        assertThatThrownBy(() -> sessionRepository.saveAndFlush(new TaskSession(userId, segunda, Instant.now(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- transições inválidas ----------

    @Test
    void deveRetornar409AoIniciarUmaTarefaJaIniciadaOuConcluida() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());

        acao(auth, "start", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("resume")));

        acao(auth, "finish", tarefa).andExpect(status().isOk());
        acao(auth, "start", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("concluída")));
    }

    @Test
    void deveRetornar409AoRetomarQuandoJaEstaRodandoNaoIniciadaOuConcluida() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        // Nunca iniciada
        acao(auth, "resume", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("start")));

        // Já rodando
        acao(auth, "start", tarefa).andExpect(status().isOk());
        acao(auth, "resume", tarefa)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("já está em andamento")));

        // Concluída
        acao(auth, "finish", tarefa).andExpect(status().isOk());
        acao(auth, "resume", tarefa).andExpect(status().isConflict());
    }

    @Test
    void deveRetornar409AoPausarOuFinalizarUmaTarefaNaoIniciada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        acao(auth, "pause", tarefa).andExpect(status().isConflict());
        acao(auth, "finish", tarefa).andExpect(status().isConflict());
    }

    @Test
    void deveRetornar409AoPausarOuFinalizarUmaTarefaJaConcluida() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        acao(auth, "finish", tarefa).andExpect(status().isOk());

        acao(auth, "pause", tarefa).andExpect(status().isConflict());
        acao(auth, "finish", tarefa).andExpect(status().isConflict());
    }

    // ---------- o cronômetro é opcional e convive com o resto da tarefa ----------

    @Test
    void deveConcluirATarefaSemNuncaUsarOCronometro() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        mockMvc.perform(patch("/tasks/{id}/complete", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        timer(auth, tarefa)
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.totalSeconds").value(0))
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    @Test
    void deveFecharOCronometroRodandoQuandoATarefaEConcluidaPeloEndpointDeConclusao() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        long outra = novaTarefa(auth, "Outra");
        acao(auth, "start", tarefa).andExpect(status().isOk());
        recuarSessaoAberta(tarefa, 10);

        mockMvc.perform(patch("/tasks/{id}/complete", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        // O tempo foi contabilizado e não sobrou cronômetro rodando numa tarefa concluída
        timer(auth, tarefa)
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.activeSession").doesNotExist())
                .andExpect(jsonPath("$.totalSeconds", allOf(greaterThanOrEqualTo(600), lessThanOrEqualTo(605))));
        assertThat(sessoesAbertas(tarefa)).isZero();
        // E o usuário está livre para cronometrar outra tarefa
        acao(auth, "start", outra).andExpect(status().isOk());
    }

    @Test
    void deveFecharOCronometroRodandoQuandoATarefaVoltaParaPendentePeloPut() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        long categoria = taskRepository.findById(tarefa).orElseThrow().getCategoryId();
        acao(auth, "start", tarefa).andExpect(status().isOk());

        mockMvc.perform(put("/tasks/{id}", tarefa)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Estudar", "categoryId": %d, "status": "PENDING"}""".formatted(categoria)))
                .andExpect(status().isOk());

        assertThat(sessoesAbertas(tarefa)).isZero();
        timer(auth, tarefa).andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void deveRetornarIdleEZeroParaTarefaQueNuncaFoiCronometrada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");

        timer(auth, tarefa)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.totalSeconds").value(0))
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    @Test
    void deveApagarAsSessoesQuandoATarefaEApagada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());

        mockMvc.perform(delete("/tasks/{id}", tarefa).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void deveApagarAsSessoesQuandoOUsuarioEApagado() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");
        long tarefa = novaTarefa(auth, "Estudar");
        acao(auth, "start", tarefa).andExpect(status().isOk());

        userRepository.deleteAll();

        assertThat(sessionRepository.count()).isZero();
    }

    // ---------- autenticação e isolamento ----------

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(post("/tasks/1/start")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/tasks/1/pause")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/tasks/1/resume")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/tasks/1/finish")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tasks/1/timer")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioNaoOperaNemConsultaOCronometroDeTarefaDeOutroUsuario() throws Exception {
        String ana = novoUsuario("Ana", "ana@exemplo.com");
        String bruno = novoUsuario("Bruno", "bruno@exemplo.com");
        long tarefaDaAna = novaTarefa(ana, "Da Ana");
        acao(ana, "start", tarefaDaAna).andExpect(status().isOk());

        for (String caminho : new String[] {"start", "pause", "resume", "finish"}) {
            acao(bruno, caminho, tarefaDaAna).andExpect(status().isNotFound());
        }
        timer(bruno, tarefaDaAna).andExpect(status().isNotFound());

        // O cronômetro da Ana segue rodando, intacto
        timer(ana, tarefaDaAna)
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.sessions.length()").value(1));
    }
}
