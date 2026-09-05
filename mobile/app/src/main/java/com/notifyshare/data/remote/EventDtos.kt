package com.notifyshare.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class IngestEventBody(
    val packageName: String,
    /** message | battery | network | system */
    val eventType: String,
    val senderHash: String? = null,
    /** ISO-8601 */
    val occurredAt: String,
    val dedupKey: String? = null,
    /** JSON opaco para o servidor: {"title":..,"body":..,"sender":..} */
    val content: String? = null,
)

@Serializable
data class IngestResultDto(val eventId: String, val deliveries: Int, val deduped: Boolean)

@Serializable
data class FeedItemDto(
    val deliveryId: String,
    val eventId: String,
    /** nickname de quem originou */
    val from: String,
    val packageName: String,
    val eventType: String,
    val senderHash: String? = null,
    val occurredAt: String,
    /** content | sender_only */
    val mode: String,
    val read: Boolean = false,
    /** presente so quando mode == content */
    val content: String? = null,
    /** false = quem recebe silenciou esse app: entra no feed mas nao avisa. */
    val notify: Boolean = true,
)

@Serializable
data class CountDto(val count: Long)

@Serializable
data class EventLocationDto(
    val eventId: String,
    /** Página onde o evento está (base 0). */
    val page: Int,
    /** Posição dentro da página (base 0). */
    val indexInPage: Int,
    /** Total de eventos antes deste (considerando filtros). */
    val totalBefore: Long,
)
