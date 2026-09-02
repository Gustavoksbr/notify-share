package com.notifyshare.events.adapter.web

import com.notifyshare.events.application.EventService
import com.notifyshare.events.application.FeedFilter
import com.notifyshare.events.application.FeedItemView
import com.notifyshare.events.application.IngestInput
import com.notifyshare.events.application.IngestResult
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(description = "Evento capturado no aparelho de origem. As regras ja rodaram aqui.")
data class IngestEventRequest(
    @field:NotBlank
    @field:Schema(description = "Pacote do app, ou 'system:battery' / 'system:wifi'.", example = "com.whatsapp")
    val packageName: String = "",

    @field:NotBlank
    @field:Schema(description = "message | battery | network | system", example = "message")
    val eventType: String = "",

    @field:Schema(description = "Hash do remetente (so em mensagens). Nunca o nome.")
    val senderHash: String? = null,

    @field:Schema(description = "Quando aconteceu no aparelho. ISO-8601.", example = "2026-09-01T15:12:00Z")
    val occurredAt: Instant = Instant.now(),

    @field:Schema(description = "Chave de deduplicacao calculada no aparelho.")
    val dedupKey: String? = null,

    @field:Schema(description = "Conteudo opaco para o servidor. Ausente quando a regra e 'so remetente'.")
    val content: String? = null,
)

@Schema(description = "Confirmacao de recebimento de notificacao")
data class MarkReadRequest(
    @field:NotBlank val deliveryId: String = "",
)

@RestController
@RequestMapping("/events")
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Eventos", description = "Ingestao e feed de notificacoes compartilhadas")
class EventController(private val events: EventService) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Registra um evento do aparelho de origem",
        description = "Idempotente por dedupKey. O servidor so roteia; nunca le o `content`.",
    )
    fun ingest(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: IngestEventRequest,
    ): IngestResult = events.ingest(
        me.id,
        IngestInput(
            packageName = body.packageName,
            eventType = body.eventType,
            senderHash = body.senderHash,
            occurredAt = body.occurredAt,
            dedupKey = body.dedupKey,
            content = body.content,
        ),
    )

    @GetMapping
    @Operation(summary = "Feed do destinatario", description = "Tudo que voce recebe, com filtros.")
    fun feed(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(required = false) from: String?,
        @RequestParam(name = "package", required = false) packageName: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) sender: String?,
        @RequestParam(defaultValue = "all") period: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<FeedItemView> = events.feed(
        me.id,
        FeedFilter(from, packageName, type, sender, period),
        page, size,
    )

    @GetMapping("/conversation")
    @Operation(
        summary = "Notificacoes por pessoa",
        description = "direction=received: o que ela te envia. direction=sent: o que voce envia a ela.",
    )
    fun conversation(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam with: String,
        @RequestParam(defaultValue = "received") direction: String,
        @RequestParam(name = "package", required = false) packageName: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) sender: String?,
        @RequestParam(defaultValue = "all") period: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<FeedItemView> = events.conversation(
        me.id, with, direction,
        FeedFilter(null, packageName, type, sender, period),
        page, size,
    )

    @PostMapping("/deliveries/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca uma notificacao como lida")
    fun markRead(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        events.markRead(me.id, id)

    @GetMapping("/unread-count")
    @Operation(summary = "Quantas notificacoes nao lidas")
    fun unread(@AuthenticationPrincipal me: AuthenticatedUser): Map<String, Long> =
        mapOf("count" to events.unreadCount(me.id))
}
