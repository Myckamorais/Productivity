package com.myck.productivity.common;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converte qualquer exceção na resposta padrão {@link ErrorResponse}.
 * Estende ResponseEntityExceptionHandler para que os erros do próprio Spring MVC
 * (405, 415, JSON malformado, rota inexistente...) também saiam no mesmo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Recurso não encontrado (ou de outro usuário): 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    /** Tentativas demais: 429 e o header Retry-After, que diz ao cliente quantos segundos esperar. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI(), null));
    }

    /** Regra de negócio violada: usa o status definido pela própria exceção. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request.getRequestURI(), null);
    }

    /** Sem token, ou token inválido/expirado, em rota protegida: 401 (chamado pelo SecurityErrorHandler). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(AuthenticationException ex,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Autenticação necessária: envie um token válido.",
                request.getRequestURI(), null);
    }

    /** Usuário autenticado sem permissão para o recurso: 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado.", request.getRequestURI(), null);
    }

    /**
     * Última rede de segurança: erro inesperado vira 500 com mensagem genérica.
     * O detalhe vai só para o log, para não vazar informação interna ao cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor.", request.getRequestURI(), null);
    }

    /** Falha no Bean Validation do corpo: 400 listando cada campo inválido. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(status)
                .body(body(status, "Dados inválidos.", path(request), fields));
    }

    /**
     * Corpo que não pôde ser lido. Quando o JSON é válido mas um valor não encaixa no tipo
     * (enum inexistente, data em formato errado...), aponta o campo e o que era esperado;
     * quando o JSON em si está quebrado ou ausente, devolve uma mensagem geral.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        MismatchedInputException mismatch = findMismatchedInput(ex);
        if (mismatch == null) {
            return ResponseEntity.status(status)
                    .body(body(status, "Corpo da requisição ausente ou JSON malformado.", path(request), null));
        }
        String field = fieldPath(mismatch);
        var fieldError = new ErrorResponse.FieldError(field.isEmpty() ? "body" : field, invalidValueMessage(mismatch));
        return ResponseEntity.status(status)
                .body(body(status, "Dados inválidos.", path(request), List.of(fieldError)));
    }

    /** Erros do Spring MVC (405, 415, JSON ilegível, rota inexistente): mantém o status e troca o corpo pelo padrão. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : "Requisição inválida.";
        return ResponseEntity.status(statusCode).headers(headers)
                .body(body(statusCode, message, path(request), null));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path,
            List<ErrorResponse.FieldError> fields) {
        return ResponseEntity.status(status).body(body(status, message, path, fields));
    }

    private static ErrorResponse body(HttpStatusCode status, String message, String path,
            List<ErrorResponse.FieldError> fields) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Erro";
        return new ErrorResponse(Instant.now(), status.value(), reason, message, path, fields);
    }

    // O Jackson pode vir embrulhado em outras exceções; percorre a cadeia até achar o erro de tipo
    private static MismatchedInputException findMismatchedInput(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof MismatchedInputException mismatch) {
                return mismatch;
            }
        }
        return null;
    }

    // Monta o caminho do campo com problema, ex.: "deadline" ou "items[0].name"
    private static String fieldPath(MismatchedInputException mismatch) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : mismatch.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    // Explica o que era esperado, conforme o tipo do campo; mensagem própria, sem repassar a do Jackson (traz nomes de classes)
    private static String invalidValueMessage(MismatchedInputException mismatch) {
        Class<?> type = mismatch.getTargetType();
        if (type != null && type.isEnum()) {
            String accepted = Arrays.stream(type.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
            return "Valor inválido. Valores aceitos: " + accepted + ".";
        }
        if (type != null && LocalDate.class.equals(type)) {
            return "Data inválida. Use o formato AAAA-MM-DD.";
        }
        if (type != null && TemporalAccessor.class.isAssignableFrom(type)) {
            return "Data ou hora inválida.";
        }
        return "Valor inválido para este campo.";
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
