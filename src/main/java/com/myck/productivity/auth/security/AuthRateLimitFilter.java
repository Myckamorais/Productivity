package com.myck.productivity.auth.security;

import com.myck.productivity.common.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Limite por IP no login e no cadastro, antes de qualquer trabalho (ler o corpo, consultar o banco, rodar o
 * BCrypt). Como o SecurityErrorHandler, entrega o 429 ao GlobalExceptionHandler para sair no formato padrão.
 * Não é um @Component pelo mesmo motivo do JwtAuthenticationFilter: roda só dentro da cadeia do Spring Security.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN = "/auth/login";
    static final String REGISTER = "/auth/register";

    private final AuthRateLimiter limiter;
    private final HandlerExceptionResolver resolver;

    public AuthRateLimitFilter(AuthRateLimiter limiter, HandlerExceptionResolver resolver) {
        this.limiter = limiter;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !"POST".equals(request.getMethod()) || !(LOGIN.equals(path) || REGISTER.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Optional<Duration> wait = LOGIN.equals(path(request))
                ? limiter.acquireLogin(ip)
                : limiter.acquireRegister(ip);
        if (wait.isPresent()) {
            resolver.resolveException(request, response, null, new TooManyRequestsException(wait.get()));
            return;
        }
        chain.doFilter(request, response);
    }

    // Caminho da rota sem o contexto da aplicação. Não usa getServletPath(): ele depende de como o servlet
    // está mapeado (vem vazio em alguns cenários, como no MockMvc), e o filtro deixaria de limitar sem aviso
    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
