package com.myck.productivity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origens (endereços do front-end) que o navegador pode usar para chamar a API (prefixo app.cors).
 *
 * @param allowedOrigins lista exata de origens, ex.: http://localhost:5173 e https://meu-front.exemplo.com;
 *                       vem de CORS_ALLOWED_ORIGINS, separadas por vírgula
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
