package com.notifyshare

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

/**
 * O container e declarado como bean: o Spring Boot cuida de subir, esperar e
 * derrubar, e @ServiceConnection aponta o datasource para ele sem precisar
 * mexer em propriedade nenhuma.
 *
 * Mesma versao maior do Postgres usada em desenvolvimento, de proposito — a
 * migracao e escrita em SQL de Postgres e o Hibernate valida o schema na
 * subida, entao testar contra outro banco nao provaria nada.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestContainer {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:18-alpine")
}
