package com.myck.productivity.auth;

import com.jayway.jsonpath.JsonPath;
import com.myck.productivity.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limite de tentativas no login e no cadastro, pela API. Limites pequenos para o teste ser rápido.
 * O contador fica em memória e é compartilhado pelos testes desta classe, por isso cada teste usa IPs
 * e e-mails só dele.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login-per-ip-per-minute=8",
        "app.rate-limit.register-per-ip-per-hour=3",
        "app.rate-limit.login-failures-per-email=3",
        "app.rate-limit.login-lock=PT15M"})
class RateLimitIT extends AbstractIntegrationTest {

    private static final String SENHA = "senha-segura-123";

    /** Simula a chamada vindo de um IP específico. */
    private static RequestPostProcessor doIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions cadastrar(String email, String ip) throws Exception {
        return mockMvc.perform(post("/auth/register").with(doIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoRegistro("Pessoa", email, SENHA)));
    }

    private ResultActions logar(String email, String senha, String ip) throws Exception {
        return mockMvc.perform(post("/auth/login").with(doIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoLogin(email, senha)));
    }

    @Test
    void deveBloquearOLoginDoEmailDepoisDeSenhasErradasMesmoVindoDeIpsDiferentes() throws Exception {
        // Given: conta existente e 3 senhas erradas, cada uma de um IP (atacante trocando de endereço)
        cadastrar("ana.bloqueio@exemplo.com", "10.1.0.1").andExpect(status().isCreated());
        logar("ana.bloqueio@exemplo.com", "errada-1", "10.1.0.2").andExpect(status().isUnauthorized());
        logar("ana.bloqueio@exemplo.com", "errada-2", "10.1.0.3").andExpect(status().isUnauthorized());
        logar("ana.bloqueio@exemplo.com", "errada-3", "10.1.0.4").andExpect(status().isUnauthorized());

        // When / Then: bloqueado por 15 min, até com a senha certa
        logar("ana.bloqueio@exemplo.com", SENHA, "10.1.0.5")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("Tente novamente em 15 minutos")));
    }

    @Test
    void naoDeveAfetarOutroEmailQuandoUmEstaBloqueado() throws Exception {
        cadastrar("bia.livre@exemplo.com", "10.2.0.1").andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            logar("carlos.bloqueado@exemplo.com", "errada", "10.2.0.2").andExpect(status().isUnauthorized());
        }
        logar("carlos.bloqueado@exemplo.com", "errada", "10.2.0.2").andExpect(status().isTooManyRequests());

        // Mesmo IP, outro e-mail: segue normal
        logar("bia.livre@exemplo.com", SENHA, "10.2.0.2").andExpect(status().isOk());
    }

    @Test
    void deveZerarAsFalhasQuandoOLoginDaCerto() throws Exception {
        // Given: 2 erros (limite 3), um acerto e mais 2 erros
        cadastrar("davi.acerto@exemplo.com", "10.3.0.1").andExpect(status().isCreated());
        logar("davi.acerto@exemplo.com", "errada", "10.3.0.1").andExpect(status().isUnauthorized());
        logar("davi.acerto@exemplo.com", "errada", "10.3.0.1").andExpect(status().isUnauthorized());
        logar("davi.acerto@exemplo.com", SENHA, "10.3.0.1").andExpect(status().isOk());
        logar("davi.acerto@exemplo.com", "errada", "10.3.0.1").andExpect(status().isUnauthorized());
        logar("davi.acerto@exemplo.com", "errada", "10.3.0.1").andExpect(status().isUnauthorized());

        // When / Then: o acerto zerou a contagem, então ainda não bloqueou
        logar("davi.acerto@exemplo.com", SENHA, "10.3.0.1").andExpect(status().isOk());
    }

    @Test
    void deveTratarEmailInexistenteComoSenhaErradaTambemNoBloqueio() throws Exception {
        // Senão, ver quem bloqueia e quem não bloqueia revelaria quais e-mails estão cadastrados
        for (int i = 0; i < 3; i++) {
            logar("ninguem.aqui@exemplo.com", "qualquer", "10.4.0.1").andExpect(status().isUnauthorized());
        }

        logar("ninguem.aqui@exemplo.com", "qualquer", "10.4.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void deveLimitarCadastrosPorIp() throws Exception {
        cadastrar("conta1@exemplo.com", "10.5.0.1").andExpect(status().isCreated());
        cadastrar("conta2@exemplo.com", "10.5.0.1").andExpect(status().isCreated());
        cadastrar("conta3@exemplo.com", "10.5.0.1").andExpect(status().isCreated());

        cadastrar("conta4@exemplo.com", "10.5.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Outro IP continua podendo se cadastrar
        cadastrar("conta5@exemplo.com", "10.5.0.2").andExpect(status().isCreated());
    }

    @Test
    void deveLimitarChamadasDeLoginPorIp() throws Exception {
        // Given: 8 tentativas (o limite por minuto), cada uma com um e-mail diferente para não cair no
        // bloqueio por e-mail
        for (int i = 0; i < 8; i++) {
            logar("varredura" + i + "@exemplo.com", "qualquer", "10.6.0.1").andExpect(status().isUnauthorized());
        }

        // When / Then: a 9ª do mesmo IP é recusada antes de qualquer consulta
        logar("varredura9@exemplo.com", "qualquer", "10.6.0.1").andExpect(status().isTooManyRequests());
        logar("varredura9@exemplo.com", "qualquer", "10.6.0.2").andExpect(status().isUnauthorized());
    }

    @Test
    void naoDeveLimitarOutrasRotas() throws Exception {
        // O filtro só olha POST /auth/login e /auth/register
        String auth = novoUsuarioDoIp("eva.rotas@exemplo.com", "10.7.0.1");
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/categories").with(doIp("10.7.0.1"))
                            .header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Cat " + i + "\", \"color\": \"#3B82F6\"}"))
                    .andExpect(status().isCreated());
        }
    }

    private String novoUsuarioDoIp(String email, String ip) throws Exception {
        cadastrar(email, ip).andExpect(status().isCreated());
        String corpo = logar(email, SENHA, ip).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(corpo, "$.accessToken");
    }
}
