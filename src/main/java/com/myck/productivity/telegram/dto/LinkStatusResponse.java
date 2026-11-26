package com.myck.productivity.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Situação do vínculo com o Telegram; {@code linkedAt} ausente quando não está vinculado. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkStatusResponse(boolean linked, Instant linkedAt) {
}
