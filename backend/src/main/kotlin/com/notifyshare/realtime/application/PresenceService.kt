package com.notifyshare.realtime.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.devices.application.DeviceService
import org.springframework.stereotype.Service
import java.time.Instant

data class Presence(
    val nickname: String,
    val online: Boolean,
    /** null quando online; senao, o ultimo heartbeat conhecido */
    val lastSeen: Instant?,
)

/**
 * Presenca = WebSocket vivo agora (online) OU o ultimo heartbeat de aparelho
 * ("offline ha 2h"). O heartbeat cobre o buraco entre fechar o app e o SO
 * matar a conexao.
 */
@Service
class PresenceService(
    private val realtime: RealtimePort,
    private val devices: DeviceService,
    private val users: UserRepository,
) {

    fun forNicknames(nicknames: Collection<String>): List<Presence> {
        val normalized = nicknames.map { it.trim().lowercase().removePrefix("@") }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return emptyList()

        val found = users.findAllByNicknameIn(normalized)
        val onlineIds = realtime.onlineAmong(found.map { it.id })
        val offlineIds = found.map { it.id }.filterNot { it in onlineIds }
        val lastSeenByUser = devices.lastSeenByUser(offlineIds)

        return found.map { user ->
            val online = user.id in onlineIds
            Presence(
                nickname = user.nickname,
                online = online,
                lastSeen = if (online) null else lastSeenByUser[user.id],
            )
        }
    }
}
