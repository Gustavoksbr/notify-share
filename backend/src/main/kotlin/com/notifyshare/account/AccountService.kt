package com.notifyshare.account

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.events.adapter.persistence.EventRepository
import com.notifyshare.friends.application.FriendService
import com.notifyshare.grants.application.GrantService
import com.notifyshare.messages.adapter.persistence.MessageRepository
import com.notifyshare.shared.web.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Portabilidade e exclusao de dados (LGPD). A exclusao apaga o usuario e deixa
 * o cascade do banco levar amizades, grants, regras, mensagens, eventos,
 * entregas, aparelhos e tokens (ver V11).
 */
@Service
class AccountService(
    private val users: UserRepository,
    private val friends: FriendService,
    private val grants: GrantService,
    private val messages: MessageRepository,
    private val events: EventRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun export(userId: UUID): Map<String, Any?> {
        val user = users.findById(userId).orElseThrow { NotFoundException("user_not_found", "Conta nao encontrada") }
        return mapOf(
            "exportedAt" to java.time.Instant.now().toString(),
            "profile" to mapOf(
                "nickname" to user.nickname,
                "email" to user.email,
                "createdAt" to user.createdAt.toString(),
                "google" to (user.googleSub != null),
                "privacyAcceptedAt" to user.privacyAcceptedAt?.toString(),
            ),
            "friends" to friends.listFriends(userId).map {
                mapOf("nickname" to it.nickname, "since" to it.since.toString())
            },
            "sharingWithOthers" to grants.listForSharer(userId).map { grantMap(it) },
            "receivingFromOthers" to grants.listForRecipient(userId).map { grantMap(it) },
            "messages" to messages.findAllInvolving(userId).map {
                mapOf(
                    "direction" to if (it.senderId == userId) "sent" else "received",
                    "body" to if (it.deletedAt != null) null else it.body,
                    "createdAt" to it.createdAt.toString(),
                    "editedAt" to it.editedAt?.toString(),
                    "deletedAt" to it.deletedAt?.toString(),
                )
            },
            "eventsYouShared" to events.findAllByOriginUserIdOrderByOccurredAtDesc(userId).map {
                mapOf(
                    "packageName" to it.packageName,
                    "eventType" to it.eventType,
                    "occurredAt" to it.occurredAt.toString(),
                    "content" to it.content,
                )
            },
        )
    }

    private fun grantMap(g: com.notifyshare.grants.application.GrantView) = mapOf(
        "counterpart" to g.counterpart,
        "role" to g.role,
        "status" to g.status,
        "enabledApps" to g.enabledApps,
        "createdAt" to g.createdAt.toString(),
    )

    @Transactional
    fun delete(userId: UUID) {
        if (!users.existsById(userId)) throw NotFoundException("user_not_found", "Conta nao encontrada")
        users.deleteById(userId)
        log.info("conta {} excluida a pedido do usuario", userId)
    }
}
