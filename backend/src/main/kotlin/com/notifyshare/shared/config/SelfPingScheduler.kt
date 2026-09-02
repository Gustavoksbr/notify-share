package com.notifyshare.shared.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Chama o proprio `/ping` na URL publica a cada 10 min, para o app ficar quente
 * sem depender de um monitor externo. Segura o spin-down do Render (15 min sem
 * trafego) e a pausa por inatividade do Supabase.
 *
 * Desligado quando `API_PRODUCTION_URL` esta vazio (dev/local): nao ha nada util
 * para pingar.
 */
@Component
class SelfPingScheduler(
    @Value("\${notifyshare.self-ping.url:}") productionUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val target: URI? = productionUrl.trim()
        .takeIf { it.isNotEmpty() }
        ?.let { URI.create(it.removeSuffix("/") + "/ping") }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Scheduled(fixedRate = TEN_MINUTES_MS, initialDelay = TEN_MINUTES_MS)
    fun ping() {
        val uri = target ?: return
        try {
            val response = http.send(
                HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            log.debug("self-ping {} -> {}", uri, response.statusCode())
        } catch (e: Exception) {
            log.warn("self-ping falhou: {}", e.message)
        }
    }

    private companion object {
        const val TEN_MINUTES_MS = 10L * 60 * 1000
    }
}
