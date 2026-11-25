package com.myck.productivity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integração da fundação: contexto sobe com PostgreSQL real, migrations aplicadas, health e Swagger abertos. */
class ProductivityApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveSubirOContextoEAplicarTodasAsMigrations() {
        // Then: o Flyway aplicou V1 (base) e V2 (users) sem falhas
        Integer aplicadas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deveResponderUpNoHealthSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void deveServirADocumentacaoOpenApiSemAutenticacao() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Myck Productivity API"));
    }

    @Test
    void deveRetornar401QuandoRotaProtegidaEAcessadaSemToken() throws Exception {
        mockMvc.perform(get("/qualquer-rota-protegida"))
                .andExpect(status().isUnauthorized());
    }
}
