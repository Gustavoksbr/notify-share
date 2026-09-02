package com.notifyshare.devices.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Um aparelho registrado por um usuario. Guarda o token do FCM (para push) e o
 * ultimo sinal de vida (para a presenca: "online agora", "offline ha 2h").
 *
 * `userId` e var: o mesmo aparelho fisico pode trocar de conta (tela de trocar
 * de conta), e o token continua o mesmo — so muda de dono.
 */
@Entity
@Table(name = "devices")
class Device(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "fcm_token", nullable = false, length = 512)
    var fcmToken: String,

    @Column(name = "platform", nullable = false, length = 16)
    var platform: String = "android",

    @Column(name = "device_label", length = 80)
    var deviceLabel: String? = null,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
