package com.myck.productivity.user.dto;

import com.myck.productivity.common.ValidTimeZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo do PUT /users/me/settings. Substitui as duas configurações de uma vez.
 *
 * @param timeZone              obrigatório, ex.: America/Sao_Paulo
 * @param dailyFocusGoalMinutes opcional; omitir (ou null) remove a meta diária de foco
 */
public record UserSettingsRequest(
        @NotBlank(message = "O fuso horário é obrigatório.")
        @ValidTimeZone
        String timeZone,

        @Min(value = 1, message = "A meta diária de foco deve ser de 1 a 1440 minutos.")
        @Max(value = 1440, message = "A meta diária de foco deve ser de 1 a 1440 minutos.")
        Integer dailyFocusGoalMinutes) {
}
