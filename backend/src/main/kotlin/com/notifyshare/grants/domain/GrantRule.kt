package com.notifyshare.grants.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Regra de um app dentro de um grant.
 *
 * `packageName` e o pacote real ("com.whatsapp") ou um pseudo-pacote para os
 * eventos que nao vem de app: "system:battery", "system:wifi".
 *
 * `contentMode`:
 *  - content     — repassa titulo e texto
 *  - sender_only — repassa so quem enviou, nunca o conteudo
 *  - paused      — nada e repassado, mas a config fica guardada
 */
@Entity
@Table(name = "grant_rules")
class GrantRule(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "grant_id", nullable = false)
    val grantId: UUID,

    @Column(name = "package_name", nullable = false)
    val packageName: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "content_mode", nullable = false, length = 16)
    var contentMode: String = CONTENT,

    @Column(name = "all_senders", nullable = false)
    var allSenders: Boolean = true,

    /** Termos separados por \n. Se preenchido, so entrega quando o titulo ou o
     *  corpo contem algum deles (case-insensitive). Vazio = sem filtro. */
    @Column(name = "text_filters", columnDefinition = "text")
    var textFilters: String? = null,

    /** Por padrao mensagens que parecem codigo de verificacao (OTP) nao sao
     *  compartilhadas. Ligar aqui desativa essa protecao para este app. */
    @Column(name = "allow_codes", nullable = false)
    var allowCodes: Boolean = false,

    @Column(name = "battery_threshold")
    var batteryThreshold: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    val deliversAnything: Boolean get() = enabled && contentMode != PAUSED

    companion object {
        const val CONTENT = "content"
        const val SENDER_ONLY = "sender_only"
        const val PAUSED = "paused"

        const val PKG_BATTERY = "system:battery"
        const val PKG_WIFI = "system:wifi"
    }
}
