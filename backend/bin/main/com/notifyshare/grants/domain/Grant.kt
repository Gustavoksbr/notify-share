package com.notifyshare.grants.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Um compartilhamento direcional: `sharerId` deixa `recipientId` receber as
 * notificacoes dele, sob as regras em [GrantRule]. O sentido inverso, se
 * existir, e outro grant.
 *
 * Estados:
 *  - offered_by_sharer / requested_by_recipient: pendente, esperando o outro lado
 *  - active
 *  - paused_by_sharer / paused_by_recipient: so quem pausou retoma
 *  - revoked: terminal; as regras sao apagadas junto
 */
@Entity
@Table(name = "grants")
class Grant(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "sharer_id", nullable = false)
    val sharerId: UUID,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: UUID,

    @Column(name = "status", nullable = false, length = 24)
    var status: String,

    @Column(name = "initiated_by", nullable = false)
    val initiatedBy: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    val isActive: Boolean get() = status == ACTIVE
    val isPending: Boolean get() = status == OFFERED || status == REQUESTED

    fun involves(userId: UUID): Boolean = sharerId == userId || recipientId == userId
    fun counterpart(userId: UUID): UUID = if (userId == sharerId) recipientId else sharerId

    companion object {
        const val OFFERED = "offered_by_sharer"
        const val REQUESTED = "requested_by_recipient"
        const val ACTIVE = "active"
        const val PAUSED_BY_SHARER = "paused_by_sharer"
        const val PAUSED_BY_RECIPIENT = "paused_by_recipient"
        const val REVOKED = "revoked"

        val LIVE = setOf(ACTIVE, PAUSED_BY_SHARER, PAUSED_BY_RECIPIENT)
    }
}
