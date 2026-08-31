package com.notifyshare.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /** SHA-256 em hex do token. O valor em claro so existe no aparelho. */
    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,

    /**
     * Agrupa toda a cadeia de rotacoes que nasceu de um mesmo login.
     * Apresentar um token ja substituido revoga a familia inteira.
     */
    @Column(name = "family_id", nullable = false)
    val familyId: UUID,

    @Column(name = "device_label", length = 80)
    val deviceLabel: String? = null,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "replaced_by")
    var replacedBy: UUID? = null,
) {
    val isActive: Boolean
        get() = revokedAt == null && replacedBy == null && Instant.now().isBefore(expiresAt)
}
