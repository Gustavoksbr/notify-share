package com.notifyshare.realtime.application

import com.notifyshare.friends.application.FriendService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Quando alguem fica online ou offline, avisa os amigos que estao online agora.
 * Quem esta offline nao precisa saber — vai buscar a presenca fresca ao abrir
 * a conversa de qualquer jeito.
 */
@Component
class PresenceBroadcaster(
    private val realtime: RealtimePort,
    private val friends: FriendService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPresenceChanged(event: PresenceChanged) {
        val friendIds = friends.friendIds(event.userId)
        if (friendIds.isEmpty()) return

        val onlineFriends = realtime.onlineAmong(friendIds)
        onlineFriends.forEach { friendId ->
            realtime.push(
                friendId,
                "presence",
                mapOf("user" to event.userId.toString(), "online" to event.online.toString()),
            )
        }
        log.debug("presenca de {} -> {} avisada a {} amigo(s)", event.userId, event.online, onlineFriends.size)
    }
}
