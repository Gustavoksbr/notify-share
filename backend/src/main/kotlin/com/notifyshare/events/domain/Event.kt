package com.notifyshare.events.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Um evento capturado no aparelho de origem: uma notificacao de app, uma
 * mudanca de bateria, o Wi-Fi que caiu. As regras "Jose sim, Maria nao" ja
 * rodaram no aparelho antes disto chegar aqui — o servidor so roteia.
 *
 * `content` e opaco: o servidor nunca o le para trabalhar. NULL quando a regra
 * do destinatario e "so remetente".
 */
@Entity
@Table(name = "events")
class Event(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "origin_user_id", nullable = false)
    val originUserId: UUID,

    @Column(name = "package_name", nullable = false)
    val packageName: String,

    @Column(name = "event_type", nullable = false, length = 24)
    val eventType: String,

    @Column(name = "sender_hash", length = 64)
    val senderHash: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now(),

    @Column(name = "dedup_key", length = 200)
    val dedupKey: String? = null,

    @Column(name = "content", columnDefinition = "text")
    val content: String? = null,
) {
    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_BATTERY = "battery"
        const val TYPE_NETWORK = "network"
        const val TYPE_SYSTEM = "system"

        /** Pseudo-pacote do "enviar notificacao de teste": entrega a todos os
         *  grants ativos sem exigir regra, so pra provar o pipeline ponta a ponta. */
        const val PKG_TEST = "system:test"
    }
}
