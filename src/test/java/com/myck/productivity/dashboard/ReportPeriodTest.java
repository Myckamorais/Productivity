package com.myck.productivity.dashboard;

import com.myck.productivity.dashboard.dto.ReportPeriod;
import com.myck.productivity.dashboard.dto.ReportPeriod.DateRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPeriodTest {

    private static LocalDate dia(int ano, int mes, int dia) {
        return LocalDate.of(ano, mes, dia);
    }

    @Test
    void deveIrDeSegundaADomingoNaSemana() {
        // 25/11/2026 é uma quarta-feira
        assertThat(ReportPeriod.WEEK.rangeContaining(dia(2026, 11, 25)))
                .isEqualTo(new DateRange(dia(2026, 11, 23), dia(2026, 11, 29)));
    }

    @Test
    void deveManterASemanaQuandoADataJaESegundaOuDomingo() {
        assertThat(ReportPeriod.WEEK.rangeContaining(dia(2026, 11, 23)).from()).isEqualTo(dia(2026, 11, 23));
        assertThat(ReportPeriod.WEEK.rangeContaining(dia(2026, 11, 29)).from()).isEqualTo(dia(2026, 11, 23));
    }

    @Test
    void deveAtravessarAViradaDeAnoNaSemana() {
        // 01/01/2027 é sexta: a semana começa em 28/12/2026
        assertThat(ReportPeriod.WEEK.rangeContaining(dia(2027, 1, 1)))
                .isEqualTo(new DateRange(dia(2026, 12, 28), dia(2027, 1, 3)));
    }

    @Test
    void deveCobrirOMesInteiroComOTamanhoCerto() {
        assertThat(ReportPeriod.MONTH.rangeContaining(dia(2026, 11, 25)))
                .isEqualTo(new DateRange(dia(2026, 11, 1), dia(2026, 11, 30)));
        assertThat(ReportPeriod.MONTH.rangeContaining(dia(2026, 12, 5)).to()).isEqualTo(dia(2026, 12, 31));
    }

    @Test
    void deveRespeitarFevereiroDeAnoBissextoENaoBissexto() {
        assertThat(ReportPeriod.MONTH.rangeContaining(dia(2028, 2, 10)).to()).isEqualTo(dia(2028, 2, 29));
        assertThat(ReportPeriod.MONTH.rangeContaining(dia(2026, 2, 10)).to()).isEqualTo(dia(2026, 2, 28));
    }

    @Test
    void deveCobrirOAnoInteiro() {
        assertThat(ReportPeriod.YEAR.rangeContaining(dia(2026, 11, 25)))
                .isEqualTo(new DateRange(dia(2026, 1, 1), dia(2026, 12, 31)));
    }
}
