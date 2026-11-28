package com.myck.productivity.config;

import com.myck.productivity.auth.security.AuthRateLimitFilter;
import com.myck.productivity.auth.security.AuthRateLimiter;
import com.myck.productivity.auth.security.JwtAuthenticationFilter;
import com.myck.productivity.auth.security.SecurityErrorHandler;
import com.myck.productivity.auth.service.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Segurança da API: autenticação por JWT, sem sessão. Tudo é protegido por padrão;
 * só o cadastro, o login, o health e a documentação são públicos.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            SecurityErrorHandler securityErrorHandler, AuthRateLimiter authRateLimiter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        http
                // CORS antes de tudo: o preflight (OPTIONS) do navegador não leva token e precisa ser respondido
                // aqui, e até as respostas 401 precisam dos headers CORS para o front conseguir lê-las
                .cors(Customizer.withDefaults())
                // API stateless consumida por token: sem sessão no servidor
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protege sessões baseadas em cookie; com token no header, não se aplica
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Cadastro e login: quem chama ainda não tem token
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        // Webhook do Telegram: quem chama é o Telegram, sem JWT; a rota confere o segredo no header
                        .requestMatchers(HttpMethod.POST, "/telegram/webhook").permitAll()
                        // Health sem login, para a hospedagem e o Docker verificarem se a app está de pé
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Documentação da API
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Padrão seguro: qualquer rota nova nasce protegida
                        .anyRequest().authenticated())
                // 401 (sem credencial) e 403 (sem permissão) no formato padrão de erro da API
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                // O filtro JWT roda antes do filtro de login padrão, para já chegar autenticado na autorização
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                // Limite de tentativas no login/cadastro antes do filtro JWT: barra o excesso antes de qualquer
                // trabalho (corpo, banco, BCrypt)
                .addFilterBefore(new AuthRateLimitFilter(authRateLimiter, resolver), JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS: o navegador só deixa um site em outro endereço (o front React) ler as respostas da API se ela
     * autorizar a origem dele. Só as origens configuradas são aceitas, nunca "*": com "*", qualquer site
     * aberto no navegador do usuário poderia chamar a API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Por padrão o navegador esconde do JavaScript os headers fora de uma lista básica; o Retry-After (429)
        // precisa ser liberado para o front saber quanto tempo esperar
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        // O token vai no header Authorization, não em cookie: o navegador não precisa enviar credenciais
        config.setAllowCredentials(false);
        // O navegador guarda a resposta do preflight por 1 hora, evitando um OPTIONS antes de cada chamada
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** BCrypt: hash com salt embutido e custo ajustável; é o padrão recomendado para senhas. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Relógio injetável: o JWT usa este bean, e os testes podem trocá-lo para simular token expirado. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
