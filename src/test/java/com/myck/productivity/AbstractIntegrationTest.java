package com.myck.productivity;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: aplicação inteira, PostgreSQL real em container e MockMvc.
 * Todos os *IT herdam daqui e compartilham o mesmo contexto (e o mesmo container) para a suíte ser mais rápida.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=" + AbstractIntegrationTest.JWT_SECRET,
        // O .env local tem o token verdadeiro do bot; sem isto, os testes ligariam o polling e falariam com o
        // Telegram de verdade (consumindo mensagens do bot em uso). O TelegramIT liga o bot com um token falso.
        "app.telegram.token=",
        "app.telegram.mode=webhook",
        "app.telegram.webhook-url=",
        "app.telegram.expiry-check-interval=PT24H"})
public abstract class AbstractIntegrationTest {

    /** Segredo só dos testes (32+ caracteres); o de verdade vem do .env e nunca entra no código. */
    public static final String JWT_SECRET = "segredo-somente-para-testes-0123456789";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    // Cada teste começa com a tabela de usuários vazia, para não depender de dados deixados por outro
    @BeforeEach
    void limparUsuarios() {
        userRepository.deleteAll();
    }

    /** Cadastra o usuário pela API. */
    protected void registrar(String nome, String email, String senha) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro(nome, email, senha)))
                .andExpect(status().isCreated());
    }

    /** Faz login pela API e devolve o JWT. */
    protected String logar(String email, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.accessToken");
    }

    /** Cadastra e loga um usuário com senha padrão e devolve o header Authorization pronto ("Bearer ..."). */
    protected String novoUsuario(String nome, String email) throws Exception {
        registrar(nome, email, "senha-segura-123");
        return "Bearer " + logar(email, "senha-segura-123");
    }

    /** Cria uma categoria pela API e devolve o id. */
    protected long criarCategoria(String auth, String nome) throws Exception {
        return criarRecurso(auth, "/categories", """
                {"name": "%s", "color": "#3B82F6"}""".formatted(nome));
    }

    /** Cria uma meta pela API e devolve o id. */
    protected long criarMeta(String auth, String titulo) throws Exception {
        return criarRecurso(auth, "/goals", """
                {"title": "%s"}""".formatted(titulo));
    }

    /** Cria uma tarefa pela API (meta e data são opcionais; data no formato AAAA-MM-DD) e devolve o id. */
    protected long criarTarefa(String auth, String titulo, long categoriaId, Long metaId, String data)
            throws Exception {
        String meta = metaId == null ? "" : ", \"goalId\": " + metaId;
        String dia = data == null ? "" : ", \"plannedDate\": \"" + data + "\"";
        return criarRecurso(auth, "/tasks", """
                {"title": "%s", "categoryId": %d%s%s}""".formatted(titulo, categoriaId, meta, dia));
    }

    private long criarRecurso(String auth, String url, String corpo) throws Exception {
        String resposta = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return lerId(resposta);
    }

    /** Lê um campo numérico (como o id) do corpo JSON de uma resposta. */
    protected static long lerId(String corpoJson) {
        return ((Number) JsonPath.read(corpoJson, "$.id")).longValue();
    }

    protected static String corpoRegistro(String nome, String email, String senha) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"}""".formatted(nome, email, senha);
    }

    protected static String corpoLogin(String email, String senha) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, senha);
    }
}
