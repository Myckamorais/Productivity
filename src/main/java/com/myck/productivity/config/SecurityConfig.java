package com.myck.productivity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Segurança da Fase 0: só libera health e documentação; todo o resto exige autenticação.
 * O filtro JWT e as rotas de login entram na Fase 1.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                // API stateless consumida por token: sem sessão no servidor
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protege sessões baseadas em cookie; sem cookie de sessão, não se aplica
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Health sem login, para a hospedagem e o Docker verificarem se a app está de pé
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Documentação da API
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Padrão seguro: qualquer rota nova nasce protegida
                        .anyRequest().authenticated())
                // Sem credencial responde 401 (e não o 403 padrão quando não há mecanismo de login)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
