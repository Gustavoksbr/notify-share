package com.notifyshare.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Codigo de recuperacao de senha. So o SHA-256 do codigo de 6 digitos fica
 * guardado; o codigo em claro vive so no e-mail.
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "code_hash", nullable = false, length = 64)
    val codeHash: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Short = 0,
) {
    fun isUsable(now: Instant): Boolean = consumedAt == null && expiresAt.isAfter(now)
}
