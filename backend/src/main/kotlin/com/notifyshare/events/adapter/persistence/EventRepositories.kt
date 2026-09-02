package com.notifyshare.events.adapter.persistence

import com.notifyshare.events.domain.Event
import com.notifyshare.events.domain.EventDelivery
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Linha achatada do feed: entrega + evento no mesmo objeto. Montada por
 * constructor expression no JPQL para nao trazer as duas entidades inteiras.
 */
class FeedRow(
    val deliveryId: UUID,
    val eventId: UUID,
    val originUserId: UUID,
    val packageName: String,
    val eventType: String,
    val senderHash: String?,
    val occurredAt: Instant,
    val deliveredMode: String,
    val readAt: Instant?,
    val content: String?,
)

interface EventRepository : JpaRepository<Event, UUID> {

    fun findByOriginUserIdAndDedupKey(originUserId: UUID, dedupKey: String): Event?

    fun findAllByIdIn(ids: Collection<UUID>): List<Event>

    @Modifying
    @Query("delete from Event e where e.occurredAt < :cutoff")
    fun deleteOlderThan(@Param("cutoff") cutoff: Instant): Int
}

interface EventDeliveryRepository : JpaRepository<EventDelivery, UUID> {

    fun findByEventIdAndGrantId(eventId: UUID, grantId: UUID): EventDelivery?

    fun existsByEventIdAndRecipientId(eventId: UUID, recipientId: UUID): Boolean

    @Query(
        """
        select new com.notifyshare.events.adapter.persistence.FeedRow(
            d.id, e.id, e.originUserId, e.packageName, e.eventType, e.senderHash,
            e.occurredAt, d.deliveredMode, d.readAt, e.content
        )
        from EventDelivery d, Event e
        where e.id = d.eventId
          and d.recipientId = :recipientId
          and (:originUserId is null or e.originUserId = :originUserId)
          and (:packageName  is null or e.packageName  = :packageName)
          and (:eventType    is null or e.eventType    = :eventType)
          and (:senderHash   is null or e.senderHash   = :senderHash)
          and e.occurredAt >= :since
        order by e.occurredAt desc
        """
    )
    fun feed(
        @Param("recipientId") recipientId: UUID,
        @Param("originUserId") originUserId: UUID?,
        @Param("packageName") packageName: String?,
        @Param("eventType") eventType: String?,
        @Param("senderHash") senderHash: String?,
        @Param("since") since: Instant,
        pageable: Pageable,
    ): List<FeedRow>

    @Query("select count(d) from EventDelivery d where d.recipientId = :recipientId and d.readAt is null")
    fun countUnread(@Param("recipientId") recipientId: UUID): Long
}
