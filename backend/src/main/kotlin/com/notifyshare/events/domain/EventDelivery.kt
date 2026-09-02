package com.notifyshare.events.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Uma copia do evento para um destinatario, sob um grant. O estado de leitura
 * vive aqui: cada pessoa tem o proprio.
 */
@Entity
@Table(name = "event_deliveries")
class EventDelivery(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    @Column(name = "grant_id", nullable = false)
    val grantId: UUID,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: UUID,

    /** content | sender_only — o modo que valia quando a entrega foi feita. */
    @Column(name = "delivered_mode", nullable = false, length = 16)
    val deliveredMode: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "fcm_message_id", length = 200)
    var fcmMessageId: String? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,
)
