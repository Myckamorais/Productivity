package com.myck.productivity.auth.dto;

/**
 * Resposta do login.
 *
 * @param accessToken      o JWT, enviado depois no header "Authorization: Bearer ..."
 * @param tokenType        sempre "Bearer"
 * @param expiresInSeconds quanto tempo o token vale
 */
public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
