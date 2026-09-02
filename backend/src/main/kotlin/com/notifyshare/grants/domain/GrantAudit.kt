package com.notifyshare.grants.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Uma mudanca no grant, registrada para aparecer na timeline da conversa.
 * "Voce comecou a compartilhar Bateria com @mariana", "Voce liberou Telegram".
 */
@Entity
@Table(name = "grant_audit")
class GrantAudit(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "grant_id", nullable = false)
    val grantId: UUID,

    @Column(name = "actor_id", nullable = false)
    val actorId: UUID,

    @Column(name = "action", nullable = false, length = 32)
    val action: String,

    @Column(name = "detail", length = 200)
    val detail: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        const val OFFERED = "offered"
        const val REQUESTED = "requested"
        const val ACTIVATED = "activated"
        const val PAUSED = "paused"
        const val RESUMED = "resumed"
        const val REVOKED = "revoked"
        const val RULES_CHANGED = "rules_changed"
    }
}
