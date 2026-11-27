package com.myck.productivity.smoke;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test do ambiente publicado: chamadas HTTP de verdade contra a API no ar, conferindo que ela subiu
 * conectada ao banco, com segurança, CORS e as funções principais funcionando.
 *
 * Fica desligado no build normal; só roda quando recebe a URL:
 * <pre>
 *   ./mvnw test -Dtest=AmbientePublicadoSmokeTest -Dsmoke.url=https://minha-api.onrender.com
 *   (opcional) -Dsmoke.origin=https://meu-front.exemplo.com   → confere também o CORS dessa origem
 * </pre>
 * O fluxo completo cria um usuário descartável (smoke-...@smoke.invalid) e apaga a tarefa e a categoria
 * que criou; o usuário fica, porque a API não tem exclusão de conta.
 */
@EnabledIfSystemProperty(named = "smoke.url", matches = "https?://.+")
class AmbientePublicadoSmokeTest {

    // A hospedagem gratuita "dorme" sem uso: a primeira chamada pode levar perto de 1 minuto para acordar a app
    private static final Duration ESPERA_ACORDAR = Duration.ofSeconds(90);
    private static final Duration ESPERA = Duration.ofSeconds(30);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(ESPERA).build();
    private static String baseUrl;

    @BeforeAll
    static void acordarAApi() throws Exception {
        baseUrl = System.getProperty("smoke.url").replaceAll("/+$", "");
        HttpResponse<String> health = enviar(HttpRequest.newBuilder(uri("/actuator/health")).timeout(ESPERA_ACORDAR));
        assertThat(health.statusCode()).as("API acordou e respondeu o health").isEqualTo(200);
    }

    @Test
    void deveResponderUpNoHealthSemExporDetalhesDaInfraestrutura() throws Exception {
        HttpResponse<String> resposta = get("/actuator/health", null);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(resposta.body(), "$.status")).isEqualTo("UP");
        // Perfil prod: só UP/DOWN, sem detalhes do banco e do disco
        assertThat(resposta.body()).doesNotContain("components", "postgres", "diskSpace");
    }

    @Test
    void deveRecusarRotaProtegidaSemTokenNoFormatoPadraoDeErro() throws Exception {
        HttpResponse<String> resposta = get("/users/me", null);

        assertThat(resposta.statusCode()).isEqualTo(401);
        assertThat((Integer) JsonPath.read(resposta.body(), "$.status")).isEqualTo(401);
    }

    @Test
    void deveServirADocumentacaoDaApi() throws Exception {
        HttpResponse<String> resposta = get("/v3/api-docs", null);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(resposta.body(), "$.info.title")).isEqualTo("Myck Productivity API");
        // Atrás do proxy HTTPS, o Swagger precisa anunciar o endereço com https (senão o "Try it out" falha)
        if (baseUrl.startsWith("https://")) {
            assertThat(resposta.body()).contains("\"url\":\"https://");
        }
    }

    @Test
    void deveAutorizarOCorsDoFrontQuandoAOrigemEInformada() throws Exception {
        String origem = System.getProperty("smoke.origin", "");
        assumeTrue(!origem.isBlank(), "sem -Dsmoke.origin, o CORS não é conferido");

        HttpResponse<String> preflight = enviar(HttpRequest.newBuilder(uri("/tasks"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origem)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type"));

        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origem);
    }

    @Test
    void deveExecutarOFluxoPrincipalDeCadastroATarefaConcluidaNoDashboard() throws Exception {
        // Given: usuário descartável, com o fuso de São Paulo
        String email = "smoke-" + UUID.randomUUID() + "@smoke.invalid";
        String senha = "senha-do-smoke-" + UUID.randomUUID();
        HttpResponse<String> cadastro = post("/auth/register", null, """
                {"name": "Smoke Test", "email": "%s", "password": "%s", "timeZone": "America/Sao_Paulo"}"""
                .formatted(email, senha));
        assertThat(cadastro.statusCode()).as("cadastro").isEqualTo(201);

        HttpResponse<String> login = post("/auth/login", null, """
                {"email": "%s", "password": "%s"}""".formatted(email, senha));
        assertThat(login.statusCode()).as("login").isEqualTo(200);
        String token = "Bearer " + JsonPath.read(login.body(), "$.accessToken");

        HttpResponse<String> eu = get("/users/me", token);
        assertThat(eu.statusCode()).as("/users/me").isEqualTo(200);
        assertThat((String) JsonPath.read(eu.body(), "$.email")).isEqualTo(email);

        // When: cria uma categoria e uma tarefa para hoje e a conclui
        HttpResponse<String> categoria = post("/categories", token, """
                {"name": "Smoke", "color": "#3B82F6"}""");
        assertThat(categoria.statusCode()).as("criar categoria").isEqualTo(201);
        long categoriaId = id(categoria);

        String hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
        HttpResponse<String> tarefa = post("/tasks", token, """
                {"title": "Tarefa do smoke test", "categoryId": %d, "plannedDate": "%s"}"""
                .formatted(categoriaId, hoje));
        assertThat(tarefa.statusCode()).as("criar tarefa").isEqualTo(201);
        long tarefaId = id(tarefa);

        HttpResponse<String> conclusao = enviar(autenticado(HttpRequest.newBuilder(uri("/tasks/" + tarefaId + "/complete")), token)
                .method("PATCH", HttpRequest.BodyPublishers.noBody()));
        assertThat(conclusao.statusCode()).as("concluir tarefa").isEqualTo(200);

        // Then: o dashboard de hoje (consulta agregada no banco) mostra a tarefa planejada e concluída
        HttpResponse<String> dashboard = get("/dashboard/today", token);
        assertThat(dashboard.statusCode()).as("dashboard").isEqualTo(200);
        assertThat((Integer) JsonPath.read(dashboard.body(), "$.plannedTasks")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(dashboard.body(), "$.plannedTasksCompleted")).isEqualTo(1);

        // Limpeza: apaga o que criou (a tarefa antes, porque categoria com tarefas não pode ser apagada)
        assertThat(delete("/tasks/" + tarefaId, token).statusCode()).as("apagar tarefa").isEqualTo(204);
        assertThat(delete("/categories/" + categoriaId, token).statusCode()).as("apagar categoria").isEqualTo(204);
    }

    // ---------- chamadas HTTP ----------

    private static URI uri(String caminho) {
        return URI.create(baseUrl + caminho);
    }

    private static HttpRequest.Builder autenticado(HttpRequest.Builder requisicao, String token) {
        return token == null ? requisicao : requisicao.header("Authorization", token);
    }

    private static HttpResponse<String> get(String caminho, String token) throws IOException, InterruptedException {
        return enviar(autenticado(HttpRequest.newBuilder(uri(caminho)), token).GET());
    }

    private static HttpResponse<String> post(String caminho, String token, String json)
            throws IOException, InterruptedException {
        return enviar(autenticado(HttpRequest.newBuilder(uri(caminho)), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private static HttpResponse<String> delete(String caminho, String token) throws IOException, InterruptedException {
        return enviar(autenticado(HttpRequest.newBuilder(uri(caminho)), token).DELETE());
    }

    private static HttpResponse<String> enviar(HttpRequest.Builder requisicao) throws IOException, InterruptedException {
        HttpRequest pronta = requisicao.build();
        HttpRequest comEspera = pronta.timeout().isPresent() ? pronta
                : HttpRequest.newBuilder(pronta, (nome, valor) -> true).timeout(ESPERA).build();
        return HTTP.send(comEspera, HttpResponse.BodyHandlers.ofString());
    }

    private static long id(HttpResponse<String> resposta) {
        return ((Number) JsonPath.read(resposta.body(), "$.id")).longValue();
    }
}
