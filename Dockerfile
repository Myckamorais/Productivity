# Imagem da API em dois estágios:
#   1) build: JDK completo + Maven Wrapper compilam e empacotam o jar
#   2) runtime: só o JRE e o jar desmontado em camadas (imagem menor, sem compilador nem código-fonte)
#
# Os testes NÃO rodam aqui: os de integração precisam de Docker (Testcontainers), e eles já rodam no
# ./mvnw verify antes do push. Este build só empacota o que foi testado.

# ---------- 1) build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Primeiro só o que define as dependências: enquanto o pom.xml não mudar, o Docker reaproveita esta
# camada e não baixa tudo de novo a cada alteração no código
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY lombok.config ./
COPY src src
# Empacota e desmonta o jar em camadas: dependências (mudam pouco) separadas do código da aplicação (muda
# sempre). O jar ganha um nome fixo para o comando de início não depender da versão do pom.xml
RUN ./mvnw -B -q package -DskipTests \
    && cp target/productivity-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- 2) runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Roda sem root: se alguém explorar uma falha na aplicação, não ganha controle do container
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Da camada que menos muda para a que mais muda, para o cache do Docker funcionar
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER app

# Perfil de produção; segredos e endereço do banco chegam como variáveis de ambiente da hospedagem
ENV SPRING_PROFILES_ACTIVE=prod
# Memória: o plano gratuito tem 512 MB. A JVM usa até 75% para o heap e deixa o resto para threads,
# metaspace e o próprio sistema. Se faltar memória, encerra (a hospedagem reinicia) em vez de ficar travada
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# A Render informa a porta em PORT (a aplicação lê); 8080 é a porta padrão para rodar localmente
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
