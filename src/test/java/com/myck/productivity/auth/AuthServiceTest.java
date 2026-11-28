package com.myck.productivity.auth;

import com.myck.productivity.auth.dto.AuthResponse;
import com.myck.productivity.auth.dto.LoginRequest;
import com.myck.productivity.auth.dto.RegisterRequest;
import com.myck.productivity.auth.service.AuthService;
import com.myck.productivity.auth.service.JwtService;
import com.myck.productivity.auth.security.AuthRateLimiter;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.common.TooManyRequestsException;
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

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthRateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, new UserMapper(), rateLimiter);
    }

    // ---------- register ----------

    @Test
    void deveCadastrarUsuarioComEmailNormalizadoESenhaCriptografada() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura-123")).thenReturn("hash-bcrypt");

        // When
        UserResponse resposta = authService.register(
                new RegisterRequest("  Ana Souza ", "  Ana@Exemplo.COM ", "senha-segura-123", null));

        // Then
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(salvo.capture());
        assertThat(salvo.getValue().getName()).isEqualTo("Ana Souza");
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(salvo.getValue().getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(resposta.email()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void deveCadastrarComOFusoPadraoQuandoNaoInformado() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);

        // When
        UserResponse resposta = authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123", null));

        // Then
        assertThat(resposta.timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(resposta.dailyFocusGoalMinutes()).isNull();
    }

    @Test
    void deveCadastrarComOFusoInformado() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);

        // When
        UserResponse resposta = authService.register(
                new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123", "Europe/Lisbon"));

        // Then
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(salvo.capture());
        assertThat(salvo.getValue().getTimeZone()).isEqualTo("Europe/Lisbon");
        assertThat(resposta.timeZone()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void deveRecusarCadastroQuandoEmailJaExiste() {
        // Given
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123", null)))
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
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura-123", null)))
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

    @Test
    void deveConferirASenhaContraUmHashFalsoQuandoOEmailNaoExisteParaOTempoNaoRevelarNada() {
        // Given: e-mail inexistente; o "hash falso" tem o mesmo custo de BCrypt dos reais
        when(userRepository.findByEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-falso");

        // When
        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem@exemplo.com", "qualquer")))
                .isInstanceOf(BusinessException.class);

        // Then: o BCrypt rodou do mesmo jeito que rodaria para um e-mail cadastrado
        verify(passwordEncoder).matches("qualquer", "hash-falso");
    }

    @Test
    void naoDeveAceitarLoginDeEmailInexistenteMesmoQueOHashFalsoConfira() {
        // Given: cenário impossível na prática (senha que bate com o hash aleatório), mas a regra não pode depender disso
        when(userRepository.findByEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-falso");
        when(passwordEncoder.matches("qualquer", "hash-falso")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem@exemplo.com", "qualquer")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void deveRegistrarAFalhaDoEmailQuandoASenhaEstaErrada() {
        User usuario = UserFactory.comId(7L);
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest(" Ana@Exemplo.com ", "errada")))
                .isInstanceOf(BusinessException.class);

        // A contagem usa o e-mail normalizado: variações de digitação contam como o mesmo e-mail
        verify(rateLimiter).loginFailed("ana@exemplo.com");
        verify(rateLimiter, never()).loginSucceeded(anyString());
    }

    @Test
    void deveZerarAsFalhasDoEmailQuandoOLoginDaCerto() {
        User usuario = UserFactory.comId(7L);
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-segura-123", usuario.getPasswordHash())).thenReturn(true);

        authService.login(new LoginRequest("ana@exemplo.com", "senha-segura-123"));

        verify(rateLimiter).loginSucceeded("ana@exemplo.com");
        verify(rateLimiter, never()).loginFailed(anyString());
    }

    @Test
    void deveRecusarCom429SemConferirASenhaQuandoOEmailEstaBloqueado() {
        // Given: tentativas erradas demais para este e-mail
        doThrow(new TooManyRequestsException(Duration.ofMinutes(15))).when(rateLimiter).checkLoginAllowed("ana@exemplo.com");

        // When / Then: nem consulta o banco nem roda o BCrypt (mesmo que a senha estivesse certa)
        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@exemplo.com", "senha-segura-123")))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verifyNoInteractions(userRepository, passwordEncoder, jwtService);
    }
}
