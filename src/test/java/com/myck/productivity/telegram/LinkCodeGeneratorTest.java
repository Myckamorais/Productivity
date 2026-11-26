package com.myck.productivity.telegram;

import com.myck.productivity.telegram.service.LinkCodeGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LinkCodeGeneratorTest {

    private final LinkCodeGenerator gerador = new LinkCodeGenerator();

    @Test
    void deveGerarCodigosDe8CaracteresSemLetrasEDigitosConfundiveis() {
        for (int i = 0; i < 1000; i++) {
            assertThat(gerador.next())
                    .hasSize(8)
                    .matches("[A-Z2-9]{8}")
                    .doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void naoDeveRepetirCodigosNumaAmostraGrande() {
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codigos.add(gerador.next());
        }

        assertThat(codigos).hasSize(10_000);
    }
}
