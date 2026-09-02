package com.notifyshare.realtime.application

import java.util.UUID

/**
 * Empurra um evento para o aparelho de alguem em tempo real, se ele estiver
 * com o app aberto. E so um atalho: a entrega garantida e sempre pelo FCM
 * (o WebSocket nao sobrevive ao Doze). Trocar WS por SSE mexe so no adapter.
 */
interface RealtimePort {

    fun push(userId: UUID, type: String, data: Map<String, String>)

    fun isOnline(userId: UUID): Boolean

    fun onlineAmong(userIds: Collection<UUID>): Set<UUID>
}
