package com.myck.productivity.dashboard.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Período de um relatório: sempre o período de calendário que contém a data de referência. */
public enum ReportPeriod {

    /** Semana de segunda a domingo (padrão ISO, o usado no Brasil). */
    WEEK {
        @Override
        public DateRange rangeContaining(LocalDate date) {
            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(monday, monday.plusDays(6));
        }
    },

    /** Mês do dia 1 ao último dia. */
    MONTH {
        @Override
        public DateRange rangeContaining(LocalDate date) {
            return new DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
        }
    },

    /** Ano de 1º de janeiro a 31 de dezembro. */
    YEAR {
        @Override
        public DateRange rangeContaining(LocalDate date) {
            return new DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()));
        }
    };

    /** O período deste tipo que contém a data informada. */
    public abstract DateRange rangeContaining(LocalDate date);

    /** Intervalo de datas inclusivo nas duas pontas. */
    public record DateRange(LocalDate from, LocalDate to) {
    }
}
