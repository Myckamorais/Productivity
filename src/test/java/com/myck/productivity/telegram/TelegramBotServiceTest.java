package com.myck.productivity.telegram;

import com.myck.productivity.category.dto.CategoryRequest;
import com.myck.productivity.category.dto.CategoryResponse;
import com.myck.productivity.category.entity.Category;
import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.category.service.CategoryService;
import com.myck.productivity.common.PageResponse;
import com.myck.productivity.task.dto.TaskRequest;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.dto.TimerState;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.service.TaskService;
import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import com.myck.productivity.telegram.entity.TelegramLink;
import com.myck.productivity.telegram.repository.TelegramLinkRepository;
import com.myck.productivity.telegram.service.TelegramBotService;
import com.myck.productivity.telegram.service.TelegramLinkService;
import com.myck.productivity.telegram.service.TelegramLinkService.LinkResult;
import com.myck.productivity.user.UserFactory;
import com.myck.productivity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    private static final long CHAT = 555L;
    private static final Long USER = 1L;
    // 26/11 01:30 UTC ainda é 25/11 em São Paulo (fuso padrão do usuário)
    private static final Instant AGORA = Instant.parse("2026-11-26T01:30:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);

    @Mock
    private TelegramClient client;
    @Mock
    private TelegramLinkService linkService;
    @Mock
    private TelegramLinkRepository linkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private CategoryRepository categoryRepository;

    private TelegramBotService bot;

    @BeforeEach
    void setUp() {
        bot = new TelegramBotService(client, linkService, linkRepository, userRepository, taskService,
                categoryService, categoryRepository, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static TelegramUpdate mensagem(String texto) {
        return new TelegramUpdate(1, new TelegramUpdate.Message(10, new TelegramUpdate.Chat(CHAT, "private"), texto));
    }

    /** Chat vinculado ao usuário, com a categoria padrão já definida. */
    private TelegramLink vinculado(Long categoriaPadrao) {
        TelegramLink link = new TelegramLink(USER);
        link.linkTo(CHAT, AGORA);
        link.setDefaultCategoryId(categoriaPadrao);
        when(linkRepository.findByChatId(CHAT)).thenReturn(Optional.of(link));
        // Lenient: alguns comandos respondem antes de precisar do fuso do usuário (ex.: /concluir sem número)
        lenient().when(userRepository.findById(USER)).thenReturn(Optional.of(UserFactory.comId(USER)));
        return link;
    }

    private String resposta() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq(CHAT), texto.capture());
        return texto.getValue();
    }

    private static TaskResponse tarefa(long id, String titulo, TaskStatus status, TaskPriority prioridade) {
        return new TaskResponse(id, titulo, null, 10L, null, prioridade, status, HOJE, null, AGORA, 0,
                TimerState.IDLE);
    }

    private void tarefasDeHoje(TaskResponse... tarefas) {
        when(taskService.list(USER, null, null, null, HOJE, HOJE, 0, 100))
                .thenReturn(new PageResponse<>(List.of(tarefas), 0, 100, tarefas.length, 1));
    }

    // ---------- o que é ignorado ----------

    @Test
    void deveIgnorarMensagensSemTexto() {
        bot.handle(new TelegramUpdate(1, new TelegramUpdate.Message(10, new TelegramUpdate.Chat(CHAT, "private"), null)));
        bot.handle(new TelegramUpdate(2, null));

        verifyNoInteractions(client, taskService, linkRepository);
    }

    @Test
    void deveIgnorarConversasEmGrupo() {
        // Em grupo, qualquer participante mexeria nas tarefas de quem vinculou
        bot.handle(new TelegramUpdate(1, new TelegramUpdate.Message(10, new TelegramUpdate.Chat(-99, "group"), "Estudar")));

        verifyNoInteractions(client, taskService, linkRepository);
    }

    // ---------- vinculação ----------

    @Test
    void deveVincularComOCodigoDoStart() {
        when(linkService.linkChat("ABCD2345", CHAT)).thenReturn(LinkResult.LINKED);

        bot.handle(mensagem("/start ABCD2345"));

        assertThat(resposta()).contains("Conta vinculada");
    }

    @Test
    void deveVincularComOComandoVincular() {
        when(linkService.linkChat("ABCD2345", CHAT)).thenReturn(LinkResult.LINKED);

        bot.handle(mensagem("/vincular ABCD2345"));

        assertThat(resposta()).contains("Conta vinculada");
    }

    @Test
    void deveAvisarQuandoOCodigoVenceuOuEInvalido() {
        when(linkService.linkChat("VENCIDO2", CHAT)).thenReturn(LinkResult.EXPIRED);
        when(linkService.linkChat("ERRADO22", CHAT)).thenReturn(LinkResult.INVALID);

        bot.handle(mensagem("/vincular VENCIDO2"));
        bot.handle(mensagem("/vincular ERRADO22"));

        ArgumentCaptor<String> textos = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).sendMessage(eq(CHAT), textos.capture());
        assertThat(textos.getAllValues().get(0)).contains("venceu");
        assertThat(textos.getAllValues().get(1)).contains("inválido");
    }

    @Test
    void deveEnsinarAVincularQuandoOChatNaoEstaVinculado() {
        when(linkRepository.findByChatId(CHAT)).thenReturn(Optional.empty());

        bot.handle(mensagem("Estudar Spring"));

        assertThat(resposta()).contains("/vincular");
        verifyNoInteractions(taskService);
    }

    @Test
    void deveDesvincularPeloComando() {
        when(linkService.unlinkChat(CHAT)).thenReturn(true);

        bot.handle(mensagem("/desvincular"));

        assertThat(resposta()).contains("desvinculado");
    }

    // ---------- criação de tarefas por texto ----------

    @Test
    void deveCriarUmaTarefaPorLinhaParaHojeNaCategoriaPadrao() {
        vinculado(10L);
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);

        bot.handle(mensagem("- Estudar Spring\n- Academia\n- Ler artigo"));

        ArgumentCaptor<TaskRequest> criadas = ArgumentCaptor.forClass(TaskRequest.class);
        verify(taskService, times(3)).create(eq(USER), criadas.capture());
        assertThat(criadas.getAllValues()).extracting(TaskRequest::title)
                .containsExactly("Estudar Spring", "Academia", "Ler artigo");
        // "Hoje" é o dia do usuário (25/11 em São Paulo), não o de UTC (26/11)
        assertThat(criadas.getAllValues()).extracting(TaskRequest::plannedDate).containsOnly(HOJE);
        assertThat(criadas.getAllValues()).extracting(TaskRequest::categoryId).containsOnly(10L);
        assertThat(resposta()).contains("3 tarefas criadas").contains("• Academia");
    }

    @Test
    void deveCriarACategoriaTelegramNaPrimeiraVezEGuardarNoVinculo() {
        TelegramLink link = vinculado(null);
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER, "Telegram")).thenReturn(Optional.empty());
        when(categoryService.create(eq(USER), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(77L, "Telegram", "#229ED9", AGORA));

        bot.handle(mensagem("Estudar"));

        verify(categoryService).create(USER, new CategoryRequest("Telegram", "#229ED9"));
        assertThat(link.getDefaultCategoryId()).isEqualTo(77L);
        verify(linkRepository).save(link);
    }

    @Test
    void deveReaproveitarACategoriaTelegramQueOUsuarioJaTinha() {
        TelegramLink link = vinculado(null);
        Category existente = new Category(USER, "telegram", "#000000");
        existente.setId(33L);
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER, "Telegram")).thenReturn(Optional.of(existente));

        bot.handle(mensagem("Estudar"));

        verify(categoryService, never()).create(any(), any());
        assertThat(link.getDefaultCategoryId()).isEqualTo(33L);
    }

    @Test
    void deveEscolherOutraCategoriaQuandoAPadraoFoiApagada() {
        TelegramLink link = vinculado(10L);
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(false);
        when(categoryRepository.findByUserIdAndNameIgnoreCase(USER, "Telegram")).thenReturn(Optional.empty());
        when(categoryService.create(eq(USER), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(78L, "Telegram", "#229ED9", AGORA));

        bot.handle(mensagem("Estudar"));

        assertThat(link.getDefaultCategoryId()).isEqualTo(78L);
    }

    @Test
    void deveAvisarQuandoAMensagemNaoTemTarefa() {
        vinculado(10L);

        bot.handle(mensagem("-\n•\n1."));

        assertThat(resposta()).contains("Não encontrei nenhuma tarefa");
        verify(taskService, never()).create(any(), any());
    }

    @Test
    void deveAvisarQuantasTarefasFicaramDeForaDoLimite() {
        vinculado(10L);
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);
        StringBuilder lista = new StringBuilder();
        for (int i = 1; i <= 23; i++) {
            lista.append("Tarefa ").append(i).append('\n');
        }

        bot.handle(mensagem(lista.toString()));

        verify(taskService, times(20)).create(eq(USER), any());
        assertThat(resposta()).contains("20 tarefas criadas").contains("3 ficaram de fora");
    }

    // ---------- /hoje ----------

    @Test
    void deveListarAsTarefasDeHojeNumeradasComSituacaoEPrioridade() {
        vinculado(10L);
        tarefasDeHoje(
                tarefa(1, "Estudar", TaskStatus.PENDING, TaskPriority.HIGH),
                tarefa(2, "Ler", TaskStatus.COMPLETED, TaskPriority.MEDIUM));

        bot.handle(mensagem("/hoje"));

        assertThat(resposta())
                .contains("Tarefas de hoje (25/11)")
                .contains("1. ⬜ Estudar ❗")
                .contains("2. ✅ Ler")
                .contains("/concluir");
    }

    @Test
    void deveDizerQuandoNaoHaTarefasHoje() {
        vinculado(10L);
        tarefasDeHoje();

        bot.handle(mensagem("/hoje"));

        assertThat(resposta()).contains("Nenhuma tarefa planejada para hoje");
    }

    @Test
    void deveExigirVinculoParaOHoje() {
        when(linkRepository.findByChatId(CHAT)).thenReturn(Optional.empty());

        bot.handle(mensagem("/hoje"));

        assertThat(resposta()).contains("/vincular");
        verifyNoInteractions(taskService);
    }

    // ---------- /concluir ----------

    @Test
    void deveConcluirPeloNumeroDoHoje() {
        vinculado(10L);
        tarefasDeHoje(
                tarefa(1, "Estudar", TaskStatus.PENDING, TaskPriority.MEDIUM),
                tarefa(2, "Ler", TaskStatus.PENDING, TaskPriority.MEDIUM),
                tarefa(3, "Academia", TaskStatus.PENDING, TaskPriority.MEDIUM));

        bot.handle(mensagem("/concluir 1 3"));

        verify(taskService).complete(USER, 1L);
        verify(taskService).complete(USER, 3L);
        verify(taskService, never()).complete(USER, 2L);
        assertThat(resposta()).contains("✅ 1. Estudar").contains("✅ 3. Academia");
    }

    @Test
    void deveConcluirUmaVezSoQuandoONumeroSeRepete() {
        vinculado(10L);
        tarefasDeHoje(tarefa(1, "Estudar", TaskStatus.PENDING, TaskPriority.MEDIUM));

        bot.handle(mensagem("/concluir 1 1"));

        verify(taskService, times(1)).complete(USER, 1L);
    }

    @Test
    void deveAvisarNumerosInvalidosEConcluirOsValidos() {
        vinculado(10L);
        tarefasDeHoje(tarefa(1, "Estudar", TaskStatus.PENDING, TaskPriority.MEDIUM));

        bot.handle(mensagem("/concluir 1 0 9 abc"));

        verify(taskService).complete(USER, 1L);
        assertThat(resposta()).contains("✅ 1. Estudar").contains("Não encontrei: 0, 9, abc");
    }

    @Test
    void naoDeveConcluirDeNovoUmaTarefaJaConcluida() {
        vinculado(10L);
        tarefasDeHoje(tarefa(1, "Estudar", TaskStatus.COMPLETED, TaskPriority.MEDIUM));

        bot.handle(mensagem("/concluir 1"));

        verify(taskService, never()).complete(anyLong(), anyLong());
        assertThat(resposta()).contains("já estava concluída");
    }

    @Test
    void devePedirONumeroQuandoOConcluirVemSemArgumento() {
        vinculado(10L);

        bot.handle(mensagem("/concluir"));

        assertThat(resposta()).contains("Exemplo: /concluir 2");
        verify(taskService, never()).complete(anyLong(), anyLong());
    }

    // ---------- outros ----------

    @Test
    void deveResponderComAAjudaParaComandoDesconhecido() {
        bot.handle(mensagem("/xpto"));

        assertThat(resposta()).contains("Não conheço esse comando").contains("/hoje");
    }

    @Test
    void deveResponderComErroGenericoSemPropagarExcecao() {
        // Uma exceção aqui viraria erro no webhook, e o Telegram reenviaria a mensagem (tarefas em dobro)
        vinculado(10L);
        when(categoryRepository.existsByIdAndUserId(10L, USER)).thenReturn(true);
        when(taskService.create(eq(USER), any())).thenThrow(new IllegalStateException("banco fora do ar"));

        bot.handle(mensagem("Estudar"));

        assertThat(resposta()).contains("Não consegui processar");
    }

    @Test
    void naoDevePropagarFalhaAoResponderNoTelegram() {
        doThrow(new TelegramClient.TelegramException("bloqueado")).when(client).sendMessage(anyLong(), anyString());

        bot.handle(mensagem("/ajuda"));

        verify(client).sendMessage(eq(CHAT), anyString());
    }
}
