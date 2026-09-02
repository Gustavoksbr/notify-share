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
    var body: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    /** Resposta a outra mensagem da mesma conversa. */
    @Column(name = "reply_to_id")
    val replyToId: UUID? = null,

    /** "Isto e sobre aquela notificacao": o evento compartilhado. */
    @Column(name = "linked_event_id")
    val linkedEventId: UUID? = null,

    @Column(name = "edited_at")
    var editedAt: Instant? = null,

    /** Soft delete: a linha fica para "mensagem apagada" e para as respostas. */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
