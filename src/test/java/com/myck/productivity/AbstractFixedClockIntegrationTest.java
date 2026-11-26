package com.myck.productivity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração que dependem de "hoje": a aplicação inteira roda com um relógio fixo em
 * {@link #AGORA} (quarta-feira, 25/11/2026, 15h em São Paulo). Com isso os dados de teste têm datas
 * conhecidas e os números do dashboard e dos relatórios podem ser conferidos exatamente.
 */
@Import(AbstractFixedClockIntegrationTest.FixedClockConfiguration.class)
public abstract class AbstractFixedClockIntegrationTest extends AbstractIntegrationTest {

    protected static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    protected static final Instant AGORA = sp(2026, 11, 25, 15, 0);

    @Autowired
    protected JdbcTemplate jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        // @Primary: substitui o relógio real em toda a aplicação (services e JWT)
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }
    }

    /** Instante de uma data e hora locais de São Paulo. */
    protected static Instant sp(int ano, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(ano, mes, dia, hora, minuto).atZone(SAO_PAULO).toInstant();
    }

    protected long idDoUsuario(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    /** Grava uma sessão de cronômetro livre já encerrada, com início e fim conhecidos. */
    protected void sessaoFechada(long userId, long taskId, Instant inicio, Instant fim) {
        jdbc.update("INSERT INTO task_sessions (user_id, task_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
                userId, taskId, utc(inicio), utc(fim));
    }

    /** Grava uma sessão de timer ainda aberta no banco (pode estar rodando ou já vencida, conforme {@link #AGORA}). */
    protected void timerAberto(long userId, long taskId, Instant inicio, int minutos) {
        jdbc.update("""
                INSERT INTO task_sessions (user_id, task_id, started_at, planned_minutes, expires_at, block_id)
                VALUES (?, ?, ?, ?, ?, gen_random_uuid())""",
                userId, taskId, utc(inicio), minutos, utc(inicio.plusSeconds(minutos * 60L)));
    }

    /** Grava uma sessão de cronômetro livre ainda rodando. */
    protected void cronometroLivreAberto(long userId, long taskId, Instant inicio) {
        jdbc.update("INSERT INTO task_sessions (user_id, task_id, started_at) VALUES (?, ?, ?)",
                userId, taskId, utc(inicio));
    }

    /** Marca a tarefa como concluída num instante conhecido. */
    protected void concluidaEm(long taskId, Instant quando) {
        jdbc.update("UPDATE tasks SET status = 'COMPLETED', completed_at = ? WHERE id = ?", utc(quando), taskId);
    }

    /** Define fuso e meta diária de foco pela API. */
    protected void configurar(String auth, String fuso, Integer metaMinutos) throws Exception {
        String meta = metaMinutos == null ? "" : ", \"dailyFocusGoalMinutes\": " + metaMinutos;
        mockMvc.perform(put("/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\": \"" + fuso + "\"" + meta + "}"))
                .andExpect(status().isOk());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
