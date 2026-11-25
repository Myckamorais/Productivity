package com.myck.productivity.auth;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.auth.service.JwtService;
import com.myck.productivity.config.JwtProperties;
import com.myck.productivity.user.UserFactory;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres";
    private static final String OUTRO_SEGREDO = "outro-segredo-totalmente-diferente-123456";

    private final JwtService jwtService = new JwtService(new JwtProperties(SEGREDO, 60), Clock.systemUTC());

    @Test
    void deveGerarTokenEExtrairOUsuarioDele() {
        // Given
        var usuario = UserFactory.comId(42L);

        // When
        String token = jwtService.generateToken(usuario);
        AuthenticatedUser autenticado = jwtService.parse(token);

        // Then
        assertThat(autenticado.id()).isEqualTo(42L);
        assertThat(autenticado.email()).isEqualTo("ana@exemplo.com");
    }

    @Test
    void deveInformarAValidadeEmSegundos() {
        assertThat(jwtService.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void deveRejeitarTokenAssinadoComOutroSegredo() {
        // Given: token legítimo, mas assinado por quem não conhece o segredo da aplicação
        JwtService falsificador = new JwtService(new JwtProperties(OUTRO_SEGREDO, 60), Clock.systemUTC());
        String tokenFalso = falsificador.generateToken(UserFactory.comId(1L));

        // When / Then
        assertThatThrownBy(() -> jwtService.parse(tokenFalso)).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenExpirado() {
        // Given: token emitido há 2 horas, com validade de 1 hora
        Clock duasHorasAtras = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String tokenAntigo = new JwtService(new JwtProperties(SEGREDO, 60), duasHorasAtras)
                .generateToken(UserFactory.comId(1L));

        // When / Then
        assertThatThrownBy(() -> jwtService.parse(tokenAntigo)).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenComPayloadAdulterado() {
        // Given: cabeçalho e assinatura do token do usuário 1 com o payload do usuário 2
        String[] doUsuario1 = jwtService.generateToken(UserFactory.comId(1L)).split("\\.");
        String[] doUsuario2 = jwtService.generateToken(UserFactory.comId(2L)).split("\\.");
        String adulterado = doUsuario1[0] + "." + doUsuario2[1] + "." + doUsuario1[2];

        // When / Then
        assertThatThrownBy(() -> jwtService.parse(adulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenMalformado() {
        assertThatThrownBy(() -> jwtService.parse("isto-nao-e-um-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void deveRejeitarTokenVazio() {
        assertThatThrownBy(() -> jwtService.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRecusarSubirComSegredoCurto() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("curto", 60), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
