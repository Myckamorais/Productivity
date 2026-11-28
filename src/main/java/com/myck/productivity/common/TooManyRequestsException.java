package com.myck.productivity.common;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** Tentativas demais em pouco tempo: responde 429 com o header Retry-After (segundos até liberar). */
public class TooManyRequestsException extends BusinessException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente em " + describe(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    // "45 segundos", "1 minuto", "15 minutos": arredonda os minutos para cima
    private static String describe(Duration wait) {
        long seconds = wait.toSeconds();
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " segundo" : " segundos");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }
}
