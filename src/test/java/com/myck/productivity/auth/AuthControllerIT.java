package com.myck.productivity.auth;

import com.myck.productivity.AbstractIntegrationTest;
import com.myck.productivity.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    // ---------- POST /auth/register ----------

    @Test
    void deveRegistrarUsuarioERetornar201SemExporASenha() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("Ana Souza", "ana@exemplo.com", "senha-segura-123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Then: no banco só existe o hash BCrypt, nunca a senha original
        User salvo = userRepository.findByEmail("ana@exemplo.com").orElseThrow();
        assertThat(salvo.getPasswordHash()).startsWith("$2").isNotEqualTo("senha-segura-123");
    }

    @Test
    void deveRegistrarComOFusoInformadoPeloFront() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "email": "ana@exemplo.com", "password": "senha-segura-123",
                                 "timeZone": "America/Manaus"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("America/Manaus"));
    }

    @Test
    void deveRetornar400QuandoOFusoDoCadastroEInvalido() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "email": "ana@exemplo.com", "password": "senha-segura-123",
                                 "timeZone": "Brasil"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("timeZone"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void deveRetornar409QuandoEmailJaEstaCadastradoMesmoEmOutraCaixa() throws Exception {
        // Given
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");

        // When / Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("Outra Ana", "ANA@Exemplo.com", "outra-senha-123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado."));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void deveRetornar400ListandoOsCamposInvalidosNoRegistro() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("", "isto-nao-e-email", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos."))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
        assertThat(userRepository.count()).isZero();
    }

    // ---------- POST /auth/login ----------

    @Test
    void deveLogarERetornarOTokenJwt() throws Exception {
        // Given
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");

        // When / Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("ana@exemplo.com", "senha-segura-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void deveAceitarLoginComEmailEmOutraCaixa() throws Exception {
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("ANA@exemplo.com", "senha-segura-123")))
                .andExpect(status().isOk());
    }

    @Test
    void deveRetornar401ComSenhaErrada() throws Exception {
        registrar("Ana", "ana@exemplo.com", "senha-segura-123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("ana@exemplo.com", "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void deveRetornar401ComEmailInexistenteComAMesmaMensagem() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("ninguem@exemplo.com", "qualquer-senha")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void deveRetornar400QuandoOLoginVemSemCampos() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }
}
