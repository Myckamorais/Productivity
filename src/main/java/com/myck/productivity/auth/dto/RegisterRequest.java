package com.myck.productivity.auth.dto;

import com.myck.productivity.common.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do POST /auth/register. */
public record RegisterRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email,

        // Máximo de 72 porque o BCrypt ignora tudo que passa de 72 bytes
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String password,

        // Opcional: o front pode mandar o fuso do navegador; sem ele, vale America/Sao_Paulo
        @ValidTimeZone
        String timeZone) {
}
