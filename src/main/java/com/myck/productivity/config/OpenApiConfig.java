package com.myck.productivity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados exibidos no Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productivityOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Myck Productivity API")
                .description("Tarefas, metas, cronômetro e dashboard de desempenho.")
                .version("0.0.1"));
    }
}
