package com.myck.productivity.common;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (ex.: e-mail já cadastrado, cronômetro já ativo). */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    /** Regra violada sem status específico: responde 422. */
    public BusinessException(String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
