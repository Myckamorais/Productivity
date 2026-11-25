package com.myck.productivity.config;

import com.myck.productivity.auth.security.JwtAuthenticationFilter;
import com.myck.productivity.auth.security.SecurityErrorHandler;
import com.myck.productivity.auth.service.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * Segurança da API: autenticação por JWT, sem sessão. Tudo é protegido por padrão;
 * só o cadastro, o login, o health e a documentação são públicos.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            SecurityErrorHandler securityErrorHandler) {
        http
                // API stateless consumida por token: sem sessão no servidor
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protege sessões baseadas em cookie; com token no header, não se aplica
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Cadastro e login: quem chama ainda não tem token
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
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
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
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
