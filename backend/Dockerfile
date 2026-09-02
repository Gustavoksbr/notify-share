# ---- build ----
# JDK 21: o toolchain do Gradle exige 21 (build.gradle.kts -> JavaLanguageVersion.of(21)).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Wrapper e arquivos de build primeiro, para a camada de dependencias so
# reconstruir quando build.gradle.kts muda.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
# -x test: os testes usam Testcontainers (precisa de Docker); rodam no CI/local.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Só o fat jar (o -plain.jar está desabilitado no build.gradle.kts).
COPY --from=build /app/build/libs/*.jar app.jar

# A Render injeta PORT em runtime; o profile "prod" ja lê server.port=${PORT:8080}.
EXPOSE 8080

# Free tier = 512 MB. Deixa o heap seguir a memoria do container, com folga
# para metaspace/threads/off-heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]
