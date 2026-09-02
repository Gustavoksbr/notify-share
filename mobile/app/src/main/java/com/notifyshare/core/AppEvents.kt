package com.notifyshare.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Barramento em memoria para "algo mudou, recarregue". O FCM e o WebSocket
 * emitem aqui; os ViewModels vivos ouvem e refazem a consulta. Se o app estiver
 * morto, a notificacao ja foi postada e a tela busca dados novos ao abrir.
 */
object AppEvents {

    const val FEED = "feed"
    const val CHAT = "chat"
    const val GRANTS = "grants"
    const val FRIENDS = "friends"
    const val PRESENCE = "presence"

    private val _bus = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val bus: SharedFlow<String> = _bus

    fun signal(topic: String) {
        _bus.tryEmit(topic)
    }
}
