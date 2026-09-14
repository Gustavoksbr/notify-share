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
    val grantId: UUID,
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

    fun findAllByOriginUserIdOrderByOccurredAtDesc(originUserId: UUID): List<Event>

    @Modifying
    @Query("delete from Event e where e.originUserId = :userId")
    fun deleteAllByOrigin(@Param("userId") userId: UUID): Int

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
            d.id, d.grantId, e.id, e.originUserId, e.packageName, e.eventType, e.senderHash,
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

    /** Total de entregas que batem com os mesmos filtros de [feed], sem paginar — usado
     *  para montar "Página X de Y" no app. */
    @Query(
        """
        select count(d)
        from EventDelivery d, Event e
        where e.id = d.eventId
          and d.recipientId = :recipientId
          and (:originUserId is null or e.originUserId = :originUserId)
          and (:packageName  is null or e.packageName  = :packageName)
          and (:eventType    is null or e.eventType    = :eventType)
          and (:senderHash   is null or e.senderHash   = :senderHash)
          and e.occurredAt >= :since
        """
    )
    fun countFeed(
        @Param("recipientId") recipientId: UUID,
        @Param("originUserId") originUserId: UUID?,
        @Param("packageName") packageName: String?,
        @Param("eventType") eventType: String?,
        @Param("senderHash") senderHash: String?,
        @Param("since") since: Instant,
    ): Long

    @Query("select count(d) from EventDelivery d where d.recipientId = :recipientId and d.readAt is null")
    fun countUnread(@Param("recipientId") recipientId: UUID): Long

    /**
     * Conta quantas entregas existem ANTES do evento especificado (considerando
     * a ordenacao desc por occurredAt). Usado para calcular em qual pagina esta
     * um evento especifico no feed.
     */
    @Query(
        """
        select count(d)
        from EventDelivery d, Event e, Event target
        where e.id = d.eventId
          and target.id = :targetEventId
          and d.recipientId = :recipientId
          and (:originUserId is null or e.originUserId = :originUserId)
          and (:packageName  is null or e.packageName  = :packageName)
          and (:eventType    is null or e.eventType    = :eventType)
          and (:senderHash   is null or e.senderHash   = :senderHash)
          and e.occurredAt >= :since
          and e.occurredAt > target.occurredAt
        """
    )
    fun countBeforeEvent(
        @Param("recipientId") recipientId: UUID,
        @Param("targetEventId") targetEventId: UUID,
        @Param("originUserId") originUserId: UUID?,
        @Param("packageName") packageName: String?,
        @Param("eventType") eventType: String?,
        @Param("senderHash") senderHash: String?,
        @Param("since") since: Instant,
    ): Long

    /**
     * Busca entregas ao redor de um evento especifico. Retorna eventos antes e
     * depois do alvo para dar contexto (útil para scroll direto).
     */
    @Query(
        """
        select new com.notifyshare.events.adapter.persistence.FeedRow(
            d.id, d.grantId, e.id, e.originUserId, e.packageName, e.eventType, e.senderHash,
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
          and (
            e.occurredAt > (select t.occurredAt from Event t where t.id = :targetEventId)
            or e.occurredAt < (select t.occurredAt from Event t where t.id = :targetEventId)
            or e.id = :targetEventId
          )
        order by e.occurredAt desc
        """
    )
    fun feedAroundEvent(
        @Param("recipientId") recipientId: UUID,
        @Param("targetEventId") targetEventId: UUID,
        @Param("originUserId") originUserId: UUID?,
        @Param("packageName") packageName: String?,
        @Param("eventType") eventType: String?,
        @Param("senderHash") senderHash: String?,
        @Param("since") since: Instant,
        pageable: Pageable,
    ): List<FeedRow>
}
