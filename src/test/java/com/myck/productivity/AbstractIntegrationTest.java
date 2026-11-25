package com.myck.productivity;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@TestPropertySource(properties = "app.jwt.secret=" + AbstractIntegrationTest.JWT_SECRET)
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

    protected static String corpoRegistro(String nome, String email, String senha) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"}""".formatted(nome, email, senha);
    }

    protected static String corpoLogin(String email, String senha) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, senha);
    }
}
