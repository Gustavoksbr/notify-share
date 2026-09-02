package com.notifyshare.messages.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.blocks.application.BlockService
import com.notifyshare.friends.application.FriendService
import com.notifyshare.grants.application.GrantService
import com.notifyshare.messages.adapter.persistence.MessageRepository
import com.notifyshare.messages.domain.Message
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushNotifier
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Um item da timeline da conversa: mensagem de texto OU um evento de auditoria
 * de compartilhamento. As acoes aparecem na propria conversa, o que da um log
 * visivel de graca (ver design/canvas.json, linha "DETALHE").
 */
data class TimelineItem(
    /** message | audit */
    val kind: String,
    val id: UUID,
    val at: Instant,
    /** message: eu enviei. audit: eu sou o autor da acao. */
    val mine: Boolean,
    val body: String?,
    val readAt: Instant?,
    /** so em audit: offered | activated | paused | resumed | revoked | rules_changed */
    val action: String?,
)

data class MessageView(
    val id: UUID,
    val mine: Boolean,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant?,
)

@Service
class MessageService(
    private val messages: MessageRepository,
    private val users: UserRepository,
    private val friends: FriendService,
    private val grants: GrantService,
    private val blocks: BlockService,
    private val pushNotifier: PushNotifier,
) {

    @Transactional
    fun send(fromId: UUID, toNickname: String, body: String): MessageView {
        val text = body.trim()
        if (text.isEmpty()) throw ValidationException("empty_message", "A mensagem esta vazia", "body")
        if (text.length > MAX_LENGTH) throw ValidationException("message_too_long", "Mensagem longa demais", "body")

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

        val saved = messages.save(Message(senderId = fromId, recipientId = other.id, body = text))
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

    @Transactional(readOnly = true)
    fun timeline(meId: UUID, otherNickname: String, page: Int, size: Int): List<TimelineItem> {
        val other = resolve(otherNickname)
        val limit = size.coerceIn(1, 100)

        val msgItems = messages.conversation(meId, other.id, PageRequest.of(page.coerceAtLeast(0), limit))
            .map {
                TimelineItem(
                    kind = "message",
                    id = it.id,
                    at = it.createdAt,
                    mine = it.senderId == meId,
                    body = it.body,
                    readAt = it.readAt,
                    action = null,
                )
            }

        // auditoria so na primeira pagina — ela e curta e ancora no inicio da conversa
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

    private fun resolve(nickname: String) =
        users.findByNickname(nickname.trim().lowercase().removePrefix("@"))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

    private companion object {
        const val MAX_LENGTH = 4000
    }
}
