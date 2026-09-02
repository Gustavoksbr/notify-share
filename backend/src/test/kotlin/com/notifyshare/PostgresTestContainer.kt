package com.notifyshare

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Por padrao os testes sobem um Postgres em container (Testcontainers cuida de
 * subir, esperar e derrubar; @ServiceConnection aponta o datasource sozinho).
 *
 * Sem Docker: rode com `SPRING_DATASOURCE_URL` apontando para um Postgres de
 * verdade (ver scripts/run-tests-no-docker.ps1). O build define entao a
 * propriedade `notifyshare.test.use-container=false` e este bean nao e criado —
 * o datasource vem do `spring.datasource.url` normal.
 *
 * Mesma versao maior do Postgres usada em desenvolvimento, de proposito: a
 * migracao e SQL de Postgres e o Hibernate valida o schema na subida.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestContainer {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(
        name = ["notifyshare.test.use-container"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:18-alpine")
}
