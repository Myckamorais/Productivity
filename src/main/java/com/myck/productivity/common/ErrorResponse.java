package com.myck.productivity.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Formato padrão de erro devolvido por toda a API, para o front tratar todos os casos do mesmo jeito.
 * {@code fieldErrors} só aparece nos erros de validação.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    /** Um campo inválido da requisição e o motivo. */
    public record FieldError(String field, String message) {
    }
}
