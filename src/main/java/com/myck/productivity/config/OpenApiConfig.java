package com.myck.productivity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados exibidos no Swagger UI (/swagger-ui.html), incluindo o botão Authorize para o JWT. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI productivityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Myck Productivity API")
                        .description("Tarefas, metas, cronômetro e dashboard de desempenho.")
                        .version("0.0.1"))
                // Declara o esquema Bearer JWT e o aplica a todos os endpoints; o Swagger passa a enviar o header
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
