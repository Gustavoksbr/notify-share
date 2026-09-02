package com.notifyshare.messages.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Mensagem de texto entre dois amigos. Aparece na aba "Mensagens" da conversa. */
@Entity
@Table(name = "messages")
class Message(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "sender_id", nullable = false)
    val senderId: UUID,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: UUID,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    val body: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,
)
