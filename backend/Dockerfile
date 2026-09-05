# syntax=docker/dockerfile:1

# ---- build ----
# JDK 21: o toolchain do Gradle exige 21 (build.gradle.kts -> JavaLanguageVersion.of(21)).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 1) So o necessario para RESOLVER dependencias. Esta camada (e o download das
#    dependencias abaixo) so reconstroi quando build.gradle.kts/settings mudam —
#    nao a cada mudanca de codigo.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration runtimeClasspath -q || true

# 2) Agora o codigo. `clean` nao entra: o container e novo, nao ha o que limpar.
#    O cache do Gradle (~/.gradle) sobrevive entre builds pelo mount acima.
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Só o fat jar (o -plain.jar está desabilitado no build.gradle.kts).
COPY --from=build /app/build/libs/*.jar app.jar

# A Render injeta PORT em runtime; o profile "prod" ja lê server.port=${PORT:8080}.
EXPOSE 8080

# Free tier = 512 MB e CPU compartilhada:
#   MaxRAMPercentage=75      heap ~= 384 MB, resto p/ metaspace/threads/off-heap
#   UseSerialGC              heap pequeno + pouca concorrencia -> menos memoria
#                            e overhead que o G1 (sem regioes nem GC concorrente)
#   MaxMetaspaceSize=128m    teto p/ um vazamento nao derrubar o container
#   TieredStopAtLevel=1      JIT so C1: menos code cache, warmup mais rapido
#                            (pico de throughput cai, irrelevante nesse trafego)
#   Xss512k                  stacks de thread menores (o Tomcat abre varias)
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:TieredStopAtLevel=1", \
  "-Xss512k", \
  "-jar", "app.jar"]
