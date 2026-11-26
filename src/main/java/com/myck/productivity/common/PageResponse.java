package com.myck.productivity.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Formato estável de página para a API. Serializar o Page do Spring Data direto expõe campos
 * internos e não tem garantia de formato entre versões.
 *
 * @param page número da página, começando em 0
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
