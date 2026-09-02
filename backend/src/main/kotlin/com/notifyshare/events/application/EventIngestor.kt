package com.notifyshare.events.application

import com.notifyshare.events.adapter.persistence.EventRepository
import com.notifyshare.events.domain.Event
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A insercao do evento e o fan-out, numa transacao so.
 *
 * Bean separado de proposito: o [EventService] chama isto de FORA de qualquer
 * transacao, para poder capturar a violacao de unicidade do `dedup_key` (corrida
 * entre dois ingests iguais) sem carregar uma transacao ja marcada para rollback.
 */
@Service
class EventIngestor(
    private val events: EventRepository,
    private val router: EventRouter,
) {

    @Transactional
    fun insert(originUserId: UUID, input: IngestInput): IngestResult {
        val event = events.save(
            Event(
                originUserId = originUserId,
                packageName = input.packageName.trim(),
                eventType = input.eventType,
                senderHash = input.senderHash?.trim()?.takeIf { it.isNotEmpty() },
                occurredAt = input.occurredAt,
                dedupKey = input.dedupKey,
                content = input.content,
            )
        )
        val count = router.fanOut(event)
        return IngestResult(event.id, count, deduped = false)
    }
}
