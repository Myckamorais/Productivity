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

# Perfil de produção; segredos e endereço do banco chegam como variáveis de ambiente da hospedagem
ENV SPRING_PROFILES_ACTIVE=prod
# Memória: o plano gratuito tem 512 MB. O heap fica em até 384 MB (75%), deixando o resto para threads,
# metaspace e o próprio sistema. Se faltar memória, encerra (a hospedagem reinicia) em vez de ficar travada.
# Heap e coletor são FIXOS, e não calculados pela máquina, por causa do cache AOT abaixo:
#   -Xms128m: com 512 MB, o heap inicial padrão seria ~8 MB (1/64 da memória), pequeno demais para os
#             objetos guardados no cache; a JVM então se recusa a subir ("archived heap objects cannot be loaded")
#   -XX:+UseSerialGC: o coletor mais leve, adequado a 512 MB e a uma fração de CPU; fixo para o treino (na
#             máquina do build) e a execução real usarem o mesmo coletor
#   -XX:TieredStopAtLevel=1: só o compilador JIT rápido (C1). Com 0,1 CPU, o compilador otimizador (C2)
#             disputa o processador com a própria subida. Custa um pouco de desempenho máximo, irrelevante
#             para uso pessoal. Medido com 0,1 CPU e 512 MB: subida de 220 s → 156 s (cache AOT) → 92 s (+ C1)
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# Cache AOT do Java 25 (JEPs 483/514/515): uma "execução de treino" sobe o Spring uma vez durante o build e
# a JVM grava as classes já carregadas e ligadas e o perfil dos métodos mais usados em app.aot. Na subida
# real, a JVM parte desse cache em vez de refazer o trabalho, o que importa muito numa CPU fraca que
# "acorda" a API depois de dormir.
# O treino não tem banco: spring.context.exit=onRefresh encerra logo após criar todos os beans (antes de
# atender requisições), e Flyway/Hibernate são impedidos de conectar. Os segredos são valores falsos, usados
# só nesta etapa do build (não ficam na imagem como variáveis de ambiente). O cache precisa ser gerado com
# o mesmo JRE e o mesmo classpath da execução real, por isso é feito aqui, no estágio final.
RUN java -XX:AOTCacheOutput=app.aot \
        -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -DDB_PASSWORD=treino -DJWT_SECRET=segredo-falso-usado-so-no-treino-do-cache-aot \
        -jar app.jar

USER app

# A Render informa a porta em PORT (a aplicação lê); 8080 é a porta padrão para rodar localmente
EXPOSE 8080

# Atenção: se o cache estiver incompatível (outro heap, outro coletor, outro JRE), a JVM pode se recusar a
# subir. Por isso as opções de memória ficam fixas acima e o cache é sempre gerado no mesmo build da imagem
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "app.jar"]
