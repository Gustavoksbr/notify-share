plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.notifyshare"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Boot 4 usa Jackson 3, que mudou de pacote: tools.jackson, nao com.fasterxml.
    // O modulo Kotlin precisa ser o da linha 3 para casar com o ObjectMapper autoconfigurado.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // No Spring Boot 4 a autoconfiguracao do Flyway vem neste starter.
    // Depender so de org.flywaydb:flyway-core faz a aplicacao subir sem migrar nada.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // springdoc 3.x e a linha para Spring Boot 4 (a 2.x e para Boot 3).
    // A 3.0.x acompanha o Boot 4.0; a 3.1.x ja e construida contra o 4.1.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Admin SDK do Firebase: e por ele que o servidor manda push pro FCM.
    // A credencial e o firebase-service-account.json (fora do controle de versao).
    implementation("com.google.firebase:firebase-admin:9.4.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Postgres de verdade nos testes: o schema e escrito em SQL de Postgres
    // e a validacao do Hibernate reprova qualquer aproximacao com H2.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x renomeou os modulos com o prefixo testcontainers-.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Entidades JPA precisam ser abertas para o Hibernate conseguir criar proxies.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // O default do Gradle mira ~1/4 da RAM da maquina e o G1 reserva um espaco
    // virtual grande no boot; com Docker Desktop + Testcontainers junto isso
    // estoura em maquina apertada. SerialGC + heap modesto resolve.
    maxHeapSize = "512m"
    jvmArgs("-XX:+UseSerialGC")
    // Sem Docker: se SPRING_DATASOURCE_URL estiver no ambiente, os testes usam
    // esse Postgres em vez de subir um container.
    if (!System.getenv("SPRING_DATASOURCE_URL").isNullOrBlank()) {
        systemProperty("notifyshare.test.use-container", "false")
    }
}

// So o fat jar executavel do Spring Boot. Sem isto o build/libs/ tem tambem o
// "-plain.jar", e o start em producao (java -jar build/libs/*.jar) fica ambiguo.
tasks.named<Jar>("jar") {
    enabled = false
}
