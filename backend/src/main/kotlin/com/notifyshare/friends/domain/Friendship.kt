package com.notifyshare.friends.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Vinculo de amizade. Simetrico: uma linha por par, na direcao de quem pediu.
 * Recusar um pedido apaga a linha — nao guardamos "negado".
 */
@Entity
@Table(name = "friendships")
class Friendship(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "requester_id", nullable = false)
    val requesterId: UUID,

    @Column(name = "addressee_id", nullable = false)
    val addresseeId: UUID,

    @Column(name = "status", nullable = false, length = 16)
    var status: String = PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    /** O solicitante quer compartilhar as notificacoes dele com o destinatario. */
    @Column(name = "also_offer_share", nullable = false)
    var alsoOfferShare: Boolean = false,

    /** O solicitante quer receber as notificacoes do destinatario. */
    @Column(name = "also_request_share", nullable = false)
    var alsoRequestShare: Boolean = false,
) {
    fun otherSide(userId: UUID): UUID = if (requesterId == userId) addresseeId else requesterId

    companion object {
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
    }
}
