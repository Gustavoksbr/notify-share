package com.notifyshare.events.application

import com.notifyshare.events.adapter.persistence.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Apaga o historico velho. As entregas caem junto por ON DELETE CASCADE.
 * Roda de madrugada; a janela exata nao importa.
 */
@Component
class EventRetentionJob(
    private val events: EventRepository,
    private val properties: EventProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${notifyshare.events.purge-cron:0 30 3 * * *}")
    @Transactional
    fun purge() {
        val cutoff = Instant.now().minus(properties.defaultRetention)
        val removed = events.deleteOlderThan(cutoff)
        if (removed > 0) log.info("retencao: {} evento(s) apagados (anteriores a {})", removed, cutoff)
    }
}
