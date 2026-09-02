package com.notifyshare.shared.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

/**
 * Liveness sem autenticacao. O `SelfPingScheduler` bate aqui a cada 10 min para
 * o Render nao hibernar (spin-down apos 15 min sem trafego) e o projeto Supabase
 * nao ser pausado por inatividade. Por isso abre uma conexao de verdade com o
 * banco, em vez de responder da memoria.
 */
@Tag(name = "Ping")
@RestController
class PingController(private val dataSource: DataSource) {

    @Operation(summary = "Verifica se a API e o banco estao no ar")
    @GetMapping("/ping")
    fun ping(): ResponseEntity<String> {
        return runCatching {
            dataSource.connection.use { conn ->
                if (conn.isValid(DB_CHECK_TIMEOUT_SECONDS)) ResponseEntity.ok("pong") else null
            }
        }.getOrNull()
            ?: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("database unavailable")
    }

    private companion object {
        const val DB_CHECK_TIMEOUT_SECONDS = 3
    }
}
