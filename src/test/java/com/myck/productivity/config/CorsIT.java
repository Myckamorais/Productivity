package com.myck.productivity.config;

import com.myck.productivity.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS como o navegador faz: antes de um POST com token vindo de outra origem, ele manda um preflight (OPTIONS)
 * sem token. Usa a origem padrão (o Vite local, http://localhost:5173).
 */
class CorsIT extends AbstractIntegrationTest {

    private static final String FRONT = "http://localhost:5173";

    @Test
    void deveAutorizarOPreflightDoFrontSemExigirToken() throws Exception {
        mockMvc.perform(options("/tasks")
                        .header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    void deveRecusarOPreflightDeUmaOrigemNaoAutorizada() throws Exception {
        // Um site qualquer aberto no navegador do usuário não pode chamar a API em nome dele
        mockMvc.perform(options("/tasks")
                        .header(HttpHeaders.ORIGIN, "https://site-malicioso.exemplo.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void deveIncluirOHeaderCorsNaRespostaDeUmaChamadaAutenticada() throws Exception {
        String auth = novoUsuario("Ana", "ana@exemplo.com");

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));
    }

    @Test
    void deveLiberarOHeaderRetryAfterParaOFrontLerOTempoDeEsperaDo429() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.ORIGIN, FRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }

    @Test
    void deveIncluirOHeaderCorsAteNo401ParaOFrontConseguirLerOErro() throws Exception {
        // Sem o header, o navegador esconde a resposta e o front não saberia que precisa mandar para o login
        mockMvc.perform(get("/users/me").header(HttpHeaders.ORIGIN, FRONT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT));
    }

    @Test
    void naoDeveDevolverHeaderCorsParaOrigemNaoAutorizadaNumaChamadaSimples() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "https://site-malicioso.exemplo.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
