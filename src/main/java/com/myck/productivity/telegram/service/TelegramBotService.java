package com.myck.productivity.telegram.service;

import com.myck.productivity.category.dto.CategoryRequest;
import com.myck.productivity.category.entity.Category;
import com.myck.productivity.category.repository.CategoryRepository;
import com.myck.productivity.category.service.CategoryService;
import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.task.dto.TaskRequest;
import com.myck.productivity.task.dto.TaskResponse;
import com.myck.productivity.task.entity.TaskPriority;
import com.myck.productivity.task.entity.TaskStatus;
import com.myck.productivity.task.service.TaskService;
import com.myck.productivity.telegram.client.TelegramClient;
import com.myck.productivity.telegram.dto.TelegramUpdate;
import com.myck.productivity.telegram.entity.TelegramLink;
import com.myck.productivity.telegram.repository.TelegramLinkRepository;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * O bot: interpreta cada mensagem recebida e responde no mesmo chat. Mensagem de texto comum vira tarefas
 * para hoje; mensagens que começam com "/" são comandos. Tudo é feito em nome do usuário vinculado ao chat,
 * pelos mesmos services da API, então valem as mesmas regras (dono, validações, cronômetro).
 *
 * Nada aqui lança exceção para quem chamou: o webhook precisa responder 200 ao Telegram, senão ele reenvia
 * a mesma mensagem várias vezes e as tarefas seriam criadas em dobro.
 */
@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    static final String DEFAULT_CATEGORY_NAME = "Telegram";
    static final String DEFAULT_CATEGORY_COLOR = "#229ED9";
    private static final int MAX_TODAY_TASKS = 100;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    static final String HELP = """
            Como usar:
            • Mande uma lista (uma tarefa por linha) e eu crio as tarefas para hoje.
            • /hoje: mostra as tarefas de hoje, numeradas.
            • /concluir 2: conclui a tarefa 2 do /hoje (dá para mandar várias: /concluir 1 3).
            • /desvincular: desliga este chat da sua conta.""";

    static final String NOT_LINKED = """
            Este chat ainda não está vinculado a uma conta.
            No app, abra Configurações > Telegram, gere um código e mande aqui: /vincular CÓDIGO""";

    private final TelegramClient client;
    private final TelegramLinkService linkService;
    private final TelegramLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public TelegramBotService(TelegramClient client, TelegramLinkService linkService,
            TelegramLinkRepository linkRepository, UserRepository userRepository, TaskService taskService,
            CategoryService categoryService, CategoryRepository categoryRepository, Clock clock) {
        this.client = client;
        this.linkService = linkService;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    /**
     * Trata uma atualização do Telegram. Ignora o que não é texto e conversas que não são privadas (em grupo,
     * qualquer participante poderia mexer nas tarefas de quem vinculou o grupo).
     */
    public void handle(TelegramUpdate update) {
        TelegramUpdate.Message message = update.message();
        if (message == null || message.chat() == null || message.text() == null || message.text().isBlank()) {
            return;
        }
        if (!"private".equals(message.chat().type())) {
            return;
        }
        long chatId = message.chat().id();
        String text = message.text().strip();
        String reply;
        try {
            reply = text.startsWith("/") ? handleCommand(chatId, BotCommand.parse(text)) : createTasks(chatId, text);
        } catch (RuntimeException e) {
            log.error("Erro ao tratar mensagem do chat {}", chatId, e);
            reply = "Não consegui processar sua mensagem agora. Tente de novo em instantes.";
        }
        send(chatId, reply);
    }

    private String handleCommand(long chatId, BotCommand command) {
        return switch (command.name()) {
            // /start CÓDIGO é o que o Telegram manda quando o usuário abre o link de vinculação
            case "start", "vincular" -> command.args().isEmpty() ? greeting(chatId) : link(chatId, command.args().getFirst());
            case "hoje" -> withLink(chatId, this::todayList);
            case "concluir" -> withLink(chatId, link -> complete(link, command.args()));
            case "desvincular" -> linkService.unlinkChat(chatId)
                    ? "Pronto, este chat foi desvinculado da sua conta."
                    : "Este chat não estava vinculado a nenhuma conta.";
            case "ajuda", "help" -> HELP;
            default -> "Não conheço esse comando.\n\n" + HELP;
        };
    }

    private String greeting(long chatId) {
        return linkRepository.findByChatId(chatId).isPresent() ? "Olá de novo!\n\n" + HELP : "Olá!\n\n" + NOT_LINKED;
    }

    private String link(long chatId, String code) {
        return switch (linkService.linkChat(code, chatId)) {
            case LINKED -> "Conta vinculada! ✅\n\n" + HELP;
            case EXPIRED -> "Esse código venceu. Gere um novo no app e mande de novo.";
            case INVALID -> "Código inválido. Confira o código gerado no app e mande: /vincular CÓDIGO";
        };
    }

    // Comandos que mexem em tarefas exigem chat vinculado; sem vínculo, a resposta ensina a vincular
    private String withLink(long chatId, Function<TelegramLink, String> action) {
        Optional<TelegramLink> link = linkRepository.findByChatId(chatId);
        return link.map(action).orElse(NOT_LINKED);
    }

    /** Cria uma tarefa por linha, todas planejadas para hoje (no fuso do usuário) e na categoria padrão do bot. */
    private String createTasks(long chatId, String text) {
        Optional<TelegramLink> found = linkRepository.findByChatId(chatId);
        if (found.isEmpty()) {
            return NOT_LINKED;
        }
        TelegramLink link = found.get();
        TaskListParser.Result parsed = TaskListParser.parse(text);
        if (parsed.titles().isEmpty()) {
            return "Não encontrei nenhuma tarefa na mensagem. Mande uma tarefa por linha.";
        }
        LocalDate today = today(link.getUserId());
        Long categoryId = defaultCategory(link);
        for (String title : parsed.titles()) {
            taskService.create(link.getUserId(), new TaskRequest(title, null, categoryId, null, null, null, today));
        }
        StringBuilder reply = new StringBuilder()
                .append(parsed.titles().size() == 1 ? "1 tarefa criada" : parsed.titles().size() + " tarefas criadas")
                .append(" para hoje:\n");
        parsed.titles().forEach(title -> reply.append("• ").append(title).append('\n'));
        if (parsed.ignored() > 0) {
            reply.append("\n⚠️ Crio no máximo ").append(TaskListParser.MAX_TASKS)
                    .append(" por mensagem; ").append(parsed.ignored()).append(" ficaram de fora.");
        }
        return reply.toString().strip();
    }

    /**
     * Lista as tarefas planejadas para hoje, numeradas. A ordem (a da listagem da API: data e depois id) é a
     * mesma que o /concluir usa, então o número mostrado aqui é o que o usuário digita depois.
     */
    private String todayList(TelegramLink link) {
        LocalDate today = today(link.getUserId());
        List<TaskResponse> tasks = todayTasks(link.getUserId(), today);
        if (tasks.isEmpty()) {
            return "Nenhuma tarefa planejada para hoje (" + today.format(DAY_MONTH) + "). Mande uma lista para criar.";
        }
        StringBuilder reply = new StringBuilder("Tarefas de hoje (").append(today.format(DAY_MONTH)).append("):\n");
        for (int i = 0; i < tasks.size(); i++) {
            TaskResponse task = tasks.get(i);
            reply.append(i + 1).append(". ")
                    .append(task.status() == TaskStatus.COMPLETED ? "✅ " : "⬜ ")
                    .append(task.title())
                    .append(task.priority() == TaskPriority.HIGH ? " ❗" : "")
                    .append('\n');
        }
        reply.append("\nPara concluir: /concluir NÚMERO");
        return reply.toString();
    }

    /** Conclui as tarefas pelos números do /hoje. Números repetidos contam uma vez; inválidos são avisados. */
    private String complete(TelegramLink link, List<String> args) {
        if (args.isEmpty()) {
            return "Diga o número da tarefa do /hoje. Exemplo: /concluir 2";
        }
        List<TaskResponse> tasks = todayTasks(link.getUserId(), today(link.getUserId()));
        Set<Integer> numbers = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String arg : args) {
            Integer number = parseNumber(arg);
            if (number == null || number < 1 || number > tasks.size()) {
                invalid.add(arg);
            } else {
                numbers.add(number);
            }
        }
        StringBuilder reply = new StringBuilder();
        for (int number : numbers) {
            TaskResponse task = tasks.get(number - 1);
            if (task.status() == TaskStatus.COMPLETED) {
                reply.append("☑️ ").append(number).append(". ").append(task.title()).append(" (já estava concluída)\n");
            } else {
                taskService.complete(link.getUserId(), task.id());
                reply.append("✅ ").append(number).append(". ").append(task.title()).append('\n');
            }
        }
        if (!invalid.isEmpty()) {
            reply.append("\nNão encontrei: ").append(String.join(", ", invalid))
                    .append(tasks.isEmpty() ? ". Hoje não há tarefas planejadas." : ". Veja os números no /hoje.");
        }
        return reply.toString().strip();
    }

    private List<TaskResponse> todayTasks(Long userId, LocalDate today) {
        return taskService.list(userId, null, null, null, today, today, 0, MAX_TODAY_TASKS).content();
    }

    /**
     * Categoria onde o bot cria as tarefas: a guardada no vínculo, se ainda existir; senão a categoria
     * "Telegram" do usuário (criada na primeira vez). Guardar o id permite ao usuário renomeá-la à vontade.
     */
    private Long defaultCategory(TelegramLink link) {
        Long userId = link.getUserId();
        Long saved = link.getDefaultCategoryId();
        if (saved != null && categoryRepository.existsByIdAndUserId(saved, userId)) {
            return saved;
        }
        Long categoryId = categoryRepository.findByUserIdAndNameIgnoreCase(userId, DEFAULT_CATEGORY_NAME)
                .map(Category::getId)
                .orElseGet(() -> categoryService.create(userId,
                        new CategoryRequest(DEFAULT_CATEGORY_NAME, DEFAULT_CATEGORY_COLOR)).id());
        link.setDefaultCategoryId(categoryId);
        linkRepository.save(link);
        return categoryId;
    }

    // "Hoje" no fuso do usuário, como no dashboard
    private LocalDate today(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return LocalDate.ofInstant(clock.instant(), user.zoneId());
    }

    private static Integer parseNumber(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Falha ao responder (rede, usuário bloqueou o bot) não pode virar erro no webhook
    private void send(long chatId, String text) {
        try {
            client.sendMessage(chatId, text);
        } catch (TelegramClient.TelegramException e) {
            log.warn("Não foi possível responder ao chat {}: {}", chatId, e.getMessage());
        }
    }
}
