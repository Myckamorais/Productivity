package com.myck.productivity.task;

import com.myck.productivity.task.dto.StartTimerRequest;
import com.myck.productivity.task.entity.TimerDurations;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimerDurationValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 45, 60})
    void deveAceitarAsQuatroDuracoesPermitidas(int minutos) {
        assertThat(validator.validate(new StartTimerRequest(minutos))).isEmpty();
        assertThat(TimerDurations.isValid(minutos)).isTrue();
    }

    @Test
    void deveAceitarSemDuracaoParaCronometroLivre() {
        assertThat(validator.validate(new StartTimerRequest(null))).isEmpty();
        assertThat(TimerDurations.isValid(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 20, 25, 59, 61, 90, 120, -15})
    void deveRecusarQualquerOutraDuracao(int minutos) {
        var violacoes = validator.validate(new StartTimerRequest(minutos));

        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.iterator().next().getMessage()).isEqualTo("A duração deve ser 15, 30, 45 ou 60 minutos.");
        assertThat(violacoes.iterator().next().getPropertyPath()).hasToString("durationMinutes");
        assertThat(TimerDurations.isValid(minutos)).isFalse();
    }
}
