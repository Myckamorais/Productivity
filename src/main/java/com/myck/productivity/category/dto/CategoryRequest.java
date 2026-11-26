package com.myck.productivity.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corpo do POST e do PUT de /categories. */
public record CategoryRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
        String name,

        @NotBlank(message = "A cor é obrigatória.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "A cor deve estar no formato #RRGGBB.")
        String color) {
}
