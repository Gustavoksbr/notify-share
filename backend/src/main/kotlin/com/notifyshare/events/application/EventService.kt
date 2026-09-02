package com.notifyshare.events.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.events.adapter.persistence.EventDeliveryRepository
import com.notifyshare.events.adapter.persistence.EventRepository
import com.notifyshare.events.adapter.persistence.FeedRow
import com.notifyshare.events.domain.Event
import com.notifyshare.grants.domain.GrantRule
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class IngestInput(
    val packageName: String,
    val eventType: String,
    val senderHash: String? = null,
    val occurredAt: Instant,
    val dedupKey: String? = null,
    val content: String? = null,
)

data class IngestResult(val eventId: UUID, val deliveries: Int, val deduped: Boolean)

data class FeedFilter(
    val fromNickname: String? = null,
    val packageName: String? = null,
    val eventType: String? = null,
    val senderHash: String? = null,
    /** today | 7d | all */
    val period: String = "all",
)

data class FeedItemView(
    val deliveryId: UUID,
    val eventId: UUID,
    val from: String,
    val packageName: String,
    val eventType: String,
    val senderHash: String?,
    val occurredAt: Instant,
    val mode: String,
    val read: Boolean,
    val content: String?,
)

@Service
class EventService(
    private val events: EventRepository,
    private val deliveries: EventDeliveryRepository,
    private val users: UserRepository,
    private val ingestor: EventIngestor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // --- ingestao (aparelho de origem) --------------------------------------

    /**
     * Sem @Transactional de proposito: a insercao vai por [EventIngestor], e a
     * violacao de unicidade do dedup_key (corrida) e tratada aqui como "ja
     * existe" em vez de virar 500.
     */
    fun ingest(originUserId: UUID, input: IngestInput): IngestResult {
        if (input.eventType !in TYPES) {
            throw ValidationException("invalid_event_type", "eventType invalido", "eventType")
        }
        input.dedupKey?.let { key ->
            events.findByOriginUserIdAndDedupKey(originUserId, key)?.let {
                return IngestResult(it.id, 0, deduped = true)
            }
        }

        return try {
            ingestor.insert(originUserId, input)
        } catch (e: DataIntegrityViolationException) {
            val existing = input.dedupKey?.let { events.findByOriginUserIdAndDedupKey(originUserId, it) }
            log.debug("ingest duplicado por corrida no dedup_key")
            IngestResult(existing?.id ?: UUID.randomUUID(), 0, deduped = true)
        }
    }

    // --- feed do destinatario ---------------------------------------------

    @Transactional(readOnly = true)
    fun feed(recipientId: UUID, filter: FeedFilter, page: Int, size: Int): List<FeedItemView> {
        val originId = filter.fromNickname?.let { resolve(it).id }
        val rows = deliveries.feed(
            recipientId = recipientId,
            originUserId = originId,
            packageName = filter.packageName,
            eventType = filter.eventType,
            senderHash = filter.senderHash,
            since = since(filter.period),
            pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100)),
        )
        return toViews(rows)
    }

    /** NotificacoesPessoa: "ela envia" (received) x "eu envio" (sent). */
    @Transactional(readOnly = true)
    fun conversation(
        meId: UUID,
        otherNickname: String,
        direction: String,
        filter: FeedFilter,
        page: Int,
        size: Int,
    ): List<FeedItemView> {
        val other = resolve(otherNickname)
        val (recipientId, originId) = when (direction) {
            "sent" -> other.id to meId
            "received" -> meId to other.id
            else -> throw ValidationException("invalid_direction", "direction deve ser received ou sent", "direction")
        }
        val rows = deliveries.feed(
            recipientId = recipientId,
            originUserId = originId,
            packageName = filter.packageName,
            eventType = filter.eventType,
            senderHash = filter.senderHash,
            since = since(filter.period),
            pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100)),
        )
        return toViews(rows)
    }

    @Transactional
    fun markRead(meId: UUID, deliveryId: UUID) {
        val d = deliveries.findById(deliveryId)
            .orElseThrow { NotFoundException("delivery_not_found", "Notificacao nao encontrada") }
        if (d.recipientId != meId) throw ForbiddenException("not_yours", "Essa notificacao nao e sua")
        if (d.readAt == null) {
            d.readAt = Instant.now()
            deliveries.save(d)
        }
    }

    @Transactional(readOnly = true)
    fun unreadCount(meId: UUID): Long = deliveries.countUnread(meId)

    // --- internos -------------------------------------------------------

    private fun resolve(nickname: String) =
        users.findByNickname(nickname.trim().lowercase().removePrefix("@"))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

    private fun since(period: String): Instant = when (period) {
        "today" -> Instant.now().truncatedTo(ChronoUnit.DAYS)
        "7d" -> Instant.now().minus(7, ChronoUnit.DAYS)
        else -> Instant.EPOCH
    }

    private fun toViews(rows: List<FeedRow>): List<FeedItemView> {
        if (rows.isEmpty()) return emptyList()
        val names = users.findAllByIdIn(rows.map { it.originUserId }.toSet())
            .associate { it.id to it.nickname }
        return rows.map { r ->
            FeedItemView(
                deliveryId = r.deliveryId,
                eventId = r.eventId,
                from = names[r.originUserId] ?: "?",
                packageName = r.packageName,
                eventType = r.eventType,
                senderHash = r.senderHash,
                occurredAt = r.occurredAt,
                mode = r.deliveredMode,
                read = r.readAt != null,
                // o servidor guardou o conteudo, mas esta entrega e "so remetente"
                content = if (r.deliveredMode == GrantRule.CONTENT) r.content else null,
            )
        }
    }

    private companion object {
        val TYPES = setOf(
            Event.TYPE_MESSAGE, Event.TYPE_BATTERY, Event.TYPE_NETWORK, Event.TYPE_SYSTEM,
        )
    }
}
