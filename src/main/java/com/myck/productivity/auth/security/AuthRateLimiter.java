package com.myck.productivity.auth.security;

import com.myck.productivity.common.RateLimiter;
import com.myck.productivity.common.TooManyRequestsException;
import com.myck.productivity.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Proteção contra força bruta no login e cadastro em massa, em duas frentes:
 * <ul>
 *   <li><b>Por e-mail</b> (principal): depois de N senhas erradas, o login daquele e-mail fica bloqueado por um
 *       tempo, mesmo com a senha certa. Funciona ainda que o atacante troque de IP a cada tentativa.</li>
 *   <li><b>Por IP</b>: limita chamadas de login e de cadastro vindas do mesmo endereço.</li>
 * </ul>
 * O IP vem do header X-Forwarded-For repassado pela hospedagem (forward-headers-strategy); como quem chama
 * pode forjar parte desse header, o limite por IP é uma barreira complementar, não a principal.
 * Com {@code app.rate-limit.enabled=false} (testes) nada é limitado.
 */
@Component
public class AuthRateLimiter {

    private final boolean enabled;
    private final RateLimiter loginByIp;
    private final RateLimiter registerByIp;
    private final RateLimiter loginFailuresByEmail;

    public AuthRateLimiter(RateLimitProperties properties, Clock clock) {
        this.enabled = properties.enabled();
        this.loginByIp = new RateLimiter(properties.loginPerIpPerMinute(), Duration.ofMinutes(1), clock);
        this.registerByIp = new RateLimiter(properties.registerPerIpPerHour(), Duration.ofHours(1), clock);
        this.loginFailuresByEmail = new RateLimiter(properties.loginFailuresPerEmail(), properties.loginLock(), clock);
    }

    /** Conta uma chamada de login vinda deste IP; vazio = liberada, senão quanto esperar. */
    public Optional<Duration> acquireLogin(String ip) {
        return enabled ? loginByIp.tryAcquire(ip) : Optional.empty();
    }

    /** Conta um cadastro vindo deste IP; vazio = liberado, senão quanto esperar. */
    public Optional<Duration> acquireRegister(String ip) {
        return enabled ? registerByIp.tryAcquire(ip) : Optional.empty();
    }

    /** Antes de conferir a senha: se o e-mail estiver bloqueado, recusa com 429 sem nem olhar a senha. */
    public void checkLoginAllowed(String email) {
        if (enabled) {
            loginFailuresByEmail.blockedFor(email).ifPresent(wait -> {
                throw new TooManyRequestsException(wait);
            });
        }
    }

    /** Senha errada (ou e-mail inexistente, tratado igual para não revelar quais existem). */
    public void loginFailed(String email) {
        if (enabled) {
            loginFailuresByEmail.record(email);
        }
    }

    /** Login certo apaga as falhas anteriores daquele e-mail. */
    public void loginSucceeded(String email) {
        if (enabled) {
            loginFailuresByEmail.reset(email);
        }
    }
}
