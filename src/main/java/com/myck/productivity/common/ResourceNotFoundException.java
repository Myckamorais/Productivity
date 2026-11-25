package com.myck.productivity.common;

/**
 * Recurso inexistente. Também é usada quando o recurso pertence a outro usuário:
 * responder 404 em vez de 403 evita revelar que o id existe.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
