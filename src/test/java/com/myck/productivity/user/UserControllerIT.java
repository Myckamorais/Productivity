package com.myck.productivity.user;

import com.myck.productivity.AbstractIntegrationTest;
import com.myck.productivity.auth.service.JwtService;
import com.myck.productivity.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    @Test
    void deveRetornarOsDadosDoUsuarioLogadoComToken() throws Exception {
        // Given
        registrar("Ana Souza", "ana@exemplo.com", "senha-segura-123");
        String token = logar("ana@exemplo.com", "senha-segura-123");

        // When / Then
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void deveRetornarCadaUsuarioApenasComOsPropriosDados() throws Exception {
        // Given: dois usuários com tokens diferentes
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");
        registrar("Bruno", "bruno@exemplo.com", "senha-segura-456");
        String tokenDoBruno = logar("bruno@exemplo.com", "senha-segura-456");

        // When / Then: o token do Bruno só devolve o Bruno
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bruno@exemplo.com"));
    }

    @Test
    void deveRetornar401NoFormatoPadraoQuandoNaoHaToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/users/me"));
    }

    @Test
    void deveRetornar401ComTokenInvalido() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar401ComTokenSemOPrefixoBearer() throws Exception {
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");
        String token = logar("ana@exemplo.com", "senha-segura-123");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar401ComTokenExpirado() throws Exception {
        // Given: token emitido há 2 horas, com a mesma chave da aplicação e validade de 1 hora
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");
        Clock duasHorasAtras = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String tokenExpirado = new JwtService(new JwtProperties(JWT_SECRET, 60), duasHorasAtras)
                .generateToken(userRepository.findByEmail("ana@exemplo.com").orElseThrow());

        // When / Then
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenExpirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar401ComTokenAssinadoPorOutraChave() throws Exception {
        // Given: token bem formado, mas assinado por um segredo que não é o da aplicação
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");
        String tokenFalso = new JwtService(
                new JwtProperties("outro-segredo-totalmente-diferente-123456", 60), Clock.systemUTC())
                .generateToken(userRepository.findByEmail("ana@exemplo.com").orElseThrow());

        // When / Then
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFalso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar404QuandoOUsuarioDoTokenFoiRemovido() throws Exception {
        // Given: token válido de uma conta que depois foi apagada
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");
        String token = logar("ana@exemplo.com", "senha-segura-123");
        userRepository.deleteAll();

        // When / Then
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
