package com.notifyshare.messages.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.blocks.application.BlockService
import com.notifyshare.events.adapter.persistence.EventDeliveryRepository
import com.notifyshare.events.adapter.persistence.EventRepository
import com.notifyshare.friends.application.FriendService
import com.notifyshare.grants.application.GrantService
import com.notifyshare.messages.adapter.persistence.MessageRepository
import com.notifyshare.messages.domain.Message
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushNotifier
import com.notifyshare.shared.web.ConflictException
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Trecho da mensagem respondida, para mostrar acima da resposta. */
data class ReplySnippet(
    val id: UUID,
    val preview: String,
    val mine: Boolean,
    val deleted: Boolean,
)

/** "Esta mensagem e sobre aquela notificacao." So a referencia, sem conteudo. */
data class LinkedEventRef(
    val eventId: UUID,
    val packageName: String,
    val eventType: String,
    val occurredAt: Instant,
    val senderHash: String?,
    val preview: String?,
)

/**
 * Um item da timeline da conversa: mensagem de texto OU um evento de auditoria
 * de compartilhamento. As acoes aparecem na propria conversa, o que da um log
 * visivel de graca.
 */
data class TimelineItem(
    /** message | audit */
    val kind: String,
    val id: UUID,
    val at: Instant,
    /** message: eu enviei. audit: eu sou o autor da acao. */
    val mine: Boolean,
    /** null quando a mensagem foi apagada (a UI mostra o placeholder). */
    val body: String?,
    val readAt: Instant?,
    /** so em audit: offered | activated | paused | resumed | revoked */
    val action: String?,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val replyTo: ReplySnippet? = null,
    val linkedEvent: LinkedEventRef? = null,
)

data class MessageView(
    val id: UUID,
    val mine: Boolean,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant?,
    val edited: Boolean = false,
)

@Service
class MessageService(
    private val messages: MessageRepository,
    private val users: UserRepository,
    private val friends: FriendService,
    private val grants: GrantService,
    private val blocks: BlockService,
    private val events: EventRepository,
    private val deliveries: EventDeliveryRepository,
    private val pushNotifier: PushNotifier,
) {

    @Transactional
    fun send(
        fromId: UUID,
        toNickname: String,
        body: String,
        replyToId: UUID? = null,
        linkedEventId: UUID? = null,
    ): MessageView {
        val text = validateBody(body)

        val other = resolve(toNickname)
        if (other.id == fromId) throw ValidationException("self_message", "Voce nao conversa com voce mesmo")
        if (!friends.areFriends(fromId, other.id)) {
            throw ForbiddenException("not_friends", "Voces precisam ser amigos para trocar mensagens")
        }
        if (blocks.iBlocked(fromId, other.id)) {
            throw ForbiddenException(
                "you_blocked_them",
                "Voce bloqueou @${other.nickname}. Desbloqueie para enviar mensagens.",
            )
        }
        if (blocks.blockedMe(fromId, other.id)) {
            throw ForbiddenException(
                "blocked_by_them",
                "Voce foi bloqueado por @${other.nickname} e nao pode enviar mensagens.",
            )
        }

        replyToId?.let { requireInConversation(it, fromId, other.id) }
        linkedEventId?.let { requireEventVisibleTo(it, fromId) }

        val saved = messages.save(
            Message(
                senderId = fromId,
                recipientId = other.id,
                body = text,
                replyToId = replyToId,
                linkedEventId = linkedEventId,
            ),
        )
        val fromNickname = users.findById(fromId).map { it.nickname }.orElse("")
        pushNotifier.notifyUser(
            other.id,
            PushMessage(
                type = "message",
                data = mapOf(
                    "messageId" to saved.id.toString(),
                    // "from" e chave reservada do FCM
                    "sender" to fromNickname,
                    "preview" to text.take(120),
                ),
            ),
        )
        return MessageView(saved.id, mine = true, saved.body, saved.createdAt, saved.readAt)
    }

    @Transactional
    fun edit(userId: UUID, messageId: UUID, body: String): MessageView {
        val text = validateBody(body)
        val message = messages.findById(messageId)
            .orElseThrow { NotFoundException("message_not_found", "Mensagem nao encontrada") }
        if (message.senderId != userId) {
            throw ForbiddenException("not_your_message", "So da para editar as suas mensagens")
        }
        if (message.deletedAt != null) {
            throw ConflictException("message_deleted", "Essa mensagem foi apagada")
        }
        message.body = text
        message.editedAt = Instant.now()
        messages.save(message)
        pushNotifier.notifyUser(
            message.recipientId,
            PushMessage(
                type = "message_edited",
                data = mapOf("messageId" to message.id.toString(), "preview" to text.take(120)),
            ),
        )
        return MessageView(message.id, mine = true, message.body, message.createdAt, message.readAt, edited = true)
    }

    @Transactional
    fun delete(userId: UUID, messageId: UUID) {
        val message = messages.findById(messageId)
            .orElseThrow { NotFoundException("message_not_found", "Mensagem nao encontrada") }
        if (message.senderId != userId) {
            throw ForbiddenException("not_your_message", "So da para apagar as suas mensagens")
        }
        if (message.deletedAt != null) return
        message.deletedAt = Instant.now()
        message.body = ""
        messages.save(message)
        pushNotifier.notifyUser(
            message.recipientId,
            PushMessage(type = "message_deleted", data = mapOf("messageId" to message.id.toString())),
        )
    }

    @Transactional(readOnly = true)
    fun timeline(meId: UUID, otherNickname: String, page: Int, size: Int): List<TimelineItem> {
        val other = resolve(otherNickname)
        val limit = size.coerceIn(1, 100)

        val msgs = messages.conversation(meId, other.id, PageRequest.of(page.coerceAtLeast(0), limit))

        val replyTargets = messages.findAllByIdIn(msgs.mapNotNull { it.replyToId }.toSet())
            .associateBy { it.id }
        val linkedEvents = events.findAllByIdIn(msgs.mapNotNull { it.linkedEventId }.toSet())
            .associateBy { it.id }

        val msgItems = msgs.map { m ->
            TimelineItem(
                kind = "message",
                id = m.id,
                at = m.createdAt,
                mine = m.senderId == meId,
                body = if (m.deletedAt != null) null else m.body,
                readAt = m.readAt,
                action = null,
                edited = m.editedAt != null,
                deleted = m.deletedAt != null,
                replyTo = m.replyToId?.let { replyTargets[it] }?.let { r ->
                    ReplySnippet(
                        id = r.id,
                        preview = if (r.deletedAt != null) "" else r.body.take(80),
                        mine = r.senderId == meId,
                        deleted = r.deletedAt != null,
                    )
                },
                linkedEvent = m.linkedEventId?.let { linkedEvents[it] }?.let { e ->
                    // Extrair preview do content JSON se disponível
                    val preview = e.content?.let { json ->
                        // Busca campos comuns no JSON: text, body, title
                        Regex(""""(?:text|body|title)"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)?.take(100)
                    }
                    LinkedEventRef(e.id, e.packageName, e.eventType, e.occurredAt, e.senderHash, preview)
                },
            )
        }

        val auditItems = if (page == 0) {
            grants.timelineBetween(meId, other.id).map {
                TimelineItem(
                    kind = "audit",
                    id = it.id,
                    at = it.createdAt,
                    mine = it.actorId == meId,
                    body = it.detail,
                    readAt = null,
                    action = it.action,
                )
            }
        } else {
            emptyList()
        }

        return (msgItems + auditItems).sortedByDescending { it.at }
    }

    @Transactional
    fun markRead(meId: UUID, otherNickname: String) {
        val other = resolve(otherNickname)
        val updated = messages.markConversationRead(meId, other.id, Instant.now())
        if (updated > 0) {
            pushNotifier.notifyUser(
                other.id,
                PushMessage(type = "message_read", data = mapOf("by" to meId.toString())),
            )
        }
    }

    @Transactional(readOnly = true)
    fun unreadCount(meId: UUID): Long = messages.countUnread(meId)

    // --- internos -------------------------------------------------------

    private fun validateBody(body: String): String {
        val text = body.trim()
        if (text.isEmpty()) throw ValidationException("empty_message", "A mensagem esta vazia", "body")
        if (text.length > MAX_LENGTH) throw ValidationException("message_too_long", "Mensagem longa demais", "body")
        return text
    }

    private fun requireInConversation(messageId: UUID, a: UUID, b: UUID) {
        val target = messages.findById(messageId)
            .orElseThrow { NotFoundException("reply_target_not_found", "A mensagem respondida nao existe") }
        val pair = setOf(target.senderId, target.recipientId)
        if (pair != setOf(a, b)) {
            throw ValidationException("reply_wrong_conversation", "Essa mensagem nao e desta conversa")
        }
    }

    private fun requireEventVisibleTo(eventId: UUID, userId: UUID) {
        val event = events.findById(eventId)
            .orElseThrow { NotFoundException("event_not_found", "Notificacao nao encontrada") }
        val visible = event.originUserId == userId ||
            deliveries.existsByEventIdAndRecipientId(eventId, userId)
        if (!visible) throw ForbiddenException("event_not_yours", "Essa notificacao nao e sua para referenciar")
    }

    private fun resolve(nickname: String) =
        users.findByNickname(nickname.trim().lowercase().removePrefix("@"))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

    private companion object {
        const val MAX_LENGTH = 4000
    }
}
