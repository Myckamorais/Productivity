package com.myck.productivity.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** Relógio que o teste avança à mão, para simular a passagem do tempo sem esperar. */
    static final class RelogioAjustavel extends Clock {

        private Instant agora = Instant.parse("2026-11-27T12:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final RateLimiter limite = new RateLimiter(3, Duration.ofMinutes(1), relogio);

    @Test
    void deveLiberarAteOLimiteERecusarDaiEmDiante() {
        assertThat(limite.tryAcquire("ip-1")).isEmpty();
        assertThat(limite.tryAcquire("ip-1")).isEmpty();
        assertThat(limite.tryAcquire("ip-1")).isEmpty();

        assertThat(limite.tryAcquire("ip-1")).contains(Duration.ofMinutes(1));
    }

    @Test
    void deveInformarQuantoFaltaParaLiberarArredondandoParaCima() {
        // Given: limite esgotado e 20,5 s decorridos da janela de 1 minuto
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");
        relogio.avancar(Duration.ofMillis(20_500));

        // When / Then: faltam 39,5 s → 40 s
        assertThat(limite.tryAcquire("ip-1")).contains(Duration.ofSeconds(40));
    }

    @Test
    void deveRecomecarAContagemQuandoAJanelaAcaba() {
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");
        assertThat(limite.tryAcquire("ip-1")).isPresent();

        relogio.avancar(Duration.ofMinutes(1));

        assertThat(limite.tryAcquire("ip-1")).isEmpty();
    }

    @Test
    void deveContarCadaChaveSeparadamente() {
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");
        limite.tryAcquire("ip-1");

        assertThat(limite.tryAcquire("ip-2")).isEmpty();
    }

    @Test
    void deveConsultarOBloqueioSemContarTentativa() {
        // Given: duas falhas registradas (limite 3)
        limite.record("ana@exemplo.com");
        limite.record("ana@exemplo.com");

        // When / Then: consultar várias vezes não gasta tentativas
        assertThat(limite.blockedFor("ana@exemplo.com")).isEmpty();
        assertThat(limite.blockedFor("ana@exemplo.com")).isEmpty();

        // a terceira falha esgota o limite e passa a bloquear
        limite.record("ana@exemplo.com");
        assertThat(limite.blockedFor("ana@exemplo.com")).contains(Duration.ofMinutes(1));
    }

    @Test
    void deveDesbloquearQuandoAContagemEZerada() {
        limite.record("ana@exemplo.com");
        limite.record("ana@exemplo.com");
        limite.record("ana@exemplo.com");

        limite.reset("ana@exemplo.com");

        assertThat(limite.blockedFor("ana@exemplo.com")).isEmpty();
    }

    @Test
    void naoDeveBloquearChaveDesconhecida() {
        assertThat(limite.blockedFor("nunca-vista")).isEmpty();
    }

    @Test
    void deveRecusarChavesNovasQuandoOTetoDeMemoriaEstaCheioDeJanelasAtivas() {
        // Given: o teto de chaves preenchido com janelas ainda ativas (ataque com milhares de IPs)
        RateLimiter grande = new RateLimiter(1, Duration.ofMinutes(1), relogio);
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            grande.tryAcquire("ip-" + i);
        }

        // When / Then: chave nova é recusada, mas quem já estava sendo contado segue a regra normal
        assertThat(grande.tryAcquire("ip-novo")).isPresent();
        assertThat(grande.tryAcquire("ip-0")).isPresent();
    }

    @Test
    void deveLiberarEspacoDescartandoJanelasVencidasQuandoOTetoEstaCheio() {
        RateLimiter grande = new RateLimiter(1, Duration.ofMinutes(1), relogio);
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            grande.tryAcquire("ip-" + i);
        }

        relogio.avancar(Duration.ofMinutes(2));

        assertThat(grande.tryAcquire("ip-novo")).isEmpty();
    }

    @Test
    void deveRecusarConfiguracaoInvalida() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1), relogio))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO, relogio))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveDescreverOTempoDeEsperaNaMensagemDo429() {
        assertThat(new TooManyRequestsException(Duration.ofSeconds(1)).getMessage()).endsWith("em 1 segundo.");
        assertThat(new TooManyRequestsException(Duration.ofSeconds(45)).getMessage()).endsWith("em 45 segundos.");
        assertThat(new TooManyRequestsException(Duration.ofSeconds(60)).getMessage()).endsWith("em 1 minuto.");
        assertThat(new TooManyRequestsException(Duration.ofSeconds(61)).getMessage()).endsWith("em 2 minutos.");
    }
}
