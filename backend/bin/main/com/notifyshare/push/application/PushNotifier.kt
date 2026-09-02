package com.notifyshare.push.application

import com.notifyshare.devices.application.DeviceService
import com.notifyshare.realtime.application.RealtimePort
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Ponto unico para "avisar um usuario". Faz as duas coisas:
 *  1. empurra pelo WebSocket, se o app estiver aberto (instantaneo, best-effort)
 *  2. manda pelo FCM, sempre (o caminho que sobrevive ao Doze)
 *
 * O app deduplica pelo id que vai no payload. Tokens rejeitados pelo FCM sao
 * apagados aqui, para a proxima tentativa nao repetir o erro.
 */
@Service
class PushNotifier(
    private val push: PushPort,
    private val devices: DeviceService,
    private val realtime: RealtimePort,
) {

    fun notifyUsers(userIds: Collection<UUID>, message: PushMessage) {
        val ids = userIds.toSet()
        ids.forEach { realtime.push(it, message.type, message.data) }

        val tokens = devices.tokensByUser(ids).values.flatten().distinct()
        if (tokens.isEmpty()) return

        val outcomes = push.send(tokens, message)
        val dead = outcomes.filterValues { it is PushOutcome.TokenRejected }.keys
        if (dead.isNotEmpty()) devices.dropTokens(dead)
    }

    fun notifyUser(userId: UUID, message: PushMessage) = notifyUsers(listOf(userId), message)
}
