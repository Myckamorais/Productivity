package com.myck.productivity.auth;

import com.myck.productivity.auth.dto.AuthResponse;
import com.myck.productivity.auth.dto.LoginRequest;
import com.myck.productivity.auth.dto.RegisterRequest;
import com.myck.productivity.auth.service.AuthService;
import com.myck.productivity.auth.service.JwtService;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.user.UserFactory;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.mapper.UserMapper;
import com.myck.productivity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, new UserMapper());
    }

    // ---------- register ----------

    @Test
    void deveCadastrarUsuarioComEmailNormalizadoESenhaCriptografada() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura-123")).thenReturn("hash-bcrypt");

        // When
        UserResponse resposta = authService.register(
                new RegisterRequest("  Ana Souza ", "  Ana@Exemplo.COM ", "senha-segura-123"));

        // Then
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(salvo.capture());
        assertThat(salvo.getValue().getName()).isEqualTo("Ana Souza");
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(salvo.getValue().getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(resposta.email()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void deveRecusarCadastroQuandoEmailJaExiste() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCadastroQuandoOBancoRejeitaEmailDuplicadoPorConcorrencia() {
        // Given: o existsByEmail não viu o outro cadastro, mas o índice único do banco barrou o INSERT
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura-123")).thenReturn("hash");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        // When / Then
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------- login ----------

    @Test
    void deveRetornarTokenQuandoAsCredenciaisSaoValidas() {
        // Given
        User usuario = UserFactory.comId(7L);
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-segura-123", usuario.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("jwt-gerado");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        // When
        AuthResponse resposta = authService.login(new LoginRequest("  ANA@exemplo.com", "senha-segura-123"));

        // Then
        assertThat(resposta.accessToken()).isEqualTo("jwt-gerado");
        assertThat(resposta.tokenType()).isEqualTo("Bearer");
        assertThat(resposta.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void deveRecusarLoginComSenhaErrada() {
        // Given
        User usuario = UserFactory.comId(7L);
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", usuario.getPasswordHash())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@exemplo.com", "errada")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("E-mail ou senha inválidos.");
                });
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void deveRecusarLoginComEmailInexistenteComAMesmaMensagemDaSenhaErrada() {
        // Given
        when(userRepository.findByEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());

        // When / Then: mesma mensagem, para não revelar quais e-mails existem
        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem@exemplo.com", "qualquer")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("E-mail ou senha inválidos.");
                });
    }
}
