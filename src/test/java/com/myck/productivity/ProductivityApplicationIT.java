package com.myck.productivity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integração da Fase 0: aplicação inteira contra um PostgreSQL real em container (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProductivityApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveSubirOContextoEAplicarAMigrationBase() {
        // Then: o Flyway rodou a V1 e registrou no histórico
        Integer aplicadas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(1);
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
