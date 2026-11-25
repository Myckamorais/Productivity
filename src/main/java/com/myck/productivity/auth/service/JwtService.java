package com.myck.productivity.auth.service;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.config.JwtProperties;
import com.myck.productivity.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Gera e valida o token de acesso (JWT assinado com HMAC-SHA256). Não há refresh token. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        // HS256 exige chave de 256 bits; um segredo curto enfraquece a assinatura, então a app nem sobe
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = Duration.ofMinutes(properties.expirationMinutes());
        this.clock = clock;
    }

    /** Emite um token cujo subject é o id do usuário; o e-mail vai como claim para uso em logs e telas. */
    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida assinatura e validade e devolve o usuário do token.
     * Lança JwtException (ou IllegalArgumentException, se o token vier vazio) quando for inválido ou expirado.
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                // Usa o mesmo relógio do restante da app, o que também permite testar expiração
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class));
        } catch (NumberFormatException e) {
            throw new JwtException("Subject do token não é um id válido.", e);
        }
    }

    /** Validade do token em segundos, informada ao cliente no login. */
    public long expiresInSeconds() {
        return expiration.toSeconds();
    }
}
