package com.notifyshare.grants.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * "Nesse grant, o app X nao me notifica." Controlado por quem RECEBE.
 * Presenca da linha = silenciado; o evento ainda entra no feed, so o push
 * e suprimido (ver EventRouter).
 */
@Entity
@Table(name = "recipient_app_mutes")
class RecipientAppMute(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "grant_id", nullable = false)
    val grantId: UUID,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: UUID,

    @Column(name = "package_name", nullable = false)
    val packageName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
