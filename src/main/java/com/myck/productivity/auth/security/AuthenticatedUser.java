package com.myck.productivity.auth.security;

/**
 * Usuário autenticado na requisição atual, montado só com os dados do token.
 * Evita uma consulta ao banco a cada requisição; quem precisa do usuário completo busca pelo id.
 */
public record AuthenticatedUser(Long id, String email) {
}
