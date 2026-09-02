package com.notifyshare.messages.adapter.web

import com.notifyshare.messages.application.MessageService
import com.notifyshare.messages.application.MessageView
import com.notifyshare.messages.application.TimelineItem
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SendMessageRequest(
    @field:NotBlank @field:Schema(example = "consegue liberar o Telegram tambem?")
    val body: String = "",
    @field:Schema(description = "Responder a esta mensagem da conversa.")
    val replyToId: UUID? = null,
    @field:Schema(description = "Vincular a mensagem a esta notificacao (evento).")
    val linkedEventId: UUID? = null,
)

data class EditMessageRequest(
    @field:NotBlank val body: String = "",
)

@RestController
@RequestMapping("/conversations")
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Conversas", description = "Mensagens e timeline com auditoria de compartilhamento")
class MessageController(private val messages: MessageService) {

    @GetMapping("/{nickname}")
    @Operation(
        summary = "Timeline da conversa",
        description = "Mensagens de texto e eventos de auditoria de grant, do mais novo ao mais antigo.",
    )
    fun timeline(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable nickname: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<TimelineItem> = messages.timeline(me.id, nickname, page, size)

    @PostMapping("/{nickname}/messages")
    @Operation(summary = "Envia uma mensagem", description = "Aceita resposta e vinculo a uma notificacao.")
    fun send(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable nickname: String,
        @Valid @RequestBody body: SendMessageRequest,
    ): MessageView = messages.send(me.id, nickname, body.body, body.replyToId, body.linkedEventId)

    @PatchMapping("/messages/{id}")
    @Operation(summary = "Edita uma mensagem", description = "So o autor. A UI passa a mostrar '(editado)'.")
    fun edit(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable id: UUID,
        @Valid @RequestBody body: EditMessageRequest,
    ): MessageView = messages.edit(me.id, id, body.body)

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Apaga uma mensagem", description = "So o autor. Soft delete: vira 'mensagem apagada'.")
    fun delete(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        messages.delete(me.id, id)

    @PostMapping("/{nickname}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca as mensagens da conversa como lidas")
    fun markRead(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable nickname: String) =
        messages.markRead(me.id, nickname)

    @GetMapping("/unread-count")
    @Operation(summary = "Total de mensagens nao lidas")
    fun unread(@AuthenticationPrincipal me: AuthenticatedUser): Map<String, Long> =
        mapOf("count" to messages.unreadCount(me.id))
}
