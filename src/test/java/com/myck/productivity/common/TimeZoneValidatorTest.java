package com.myck.productivity.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneValidatorTest {

    private final TimeZoneValidator validator = new TimeZoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {"America/Sao_Paulo", "America/Manaus", "Europe/Lisbon", "UTC", "Asia/Tokyo"})
    void deveAceitarIdentificadoresDeRegiao(String fuso) {
        assertThat(validator.isValid(fuso, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Brasil", "America/Sao Paulo", "-03:00", "GMT-3", "BRT", "america/sao_paulo"})
    void deveRecusarOQueNaoEUmaRegiaoValida(String fuso) {
        assertThat(validator.isValid(fuso, null)).isFalse();
    }

    @Test
    void deveAceitarNuloParaOCampoOpcional() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
