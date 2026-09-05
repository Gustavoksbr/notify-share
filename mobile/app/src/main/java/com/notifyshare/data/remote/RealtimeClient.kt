package com.notifyshare.data.remote

import com.notifyshare.BuildConfig
import com.notifyshare.core.AppEvents
import com.notifyshare.data.local.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket. Vive so enquanto o app esta em primeiro plano (o
 * AppLifecycleObserver chama start/stop). Ter WebSocket vivo = estar "online"
 * para os amigos.
 *
 * A entrega garantida de eventos nunca depende disto: quando o app fecha, o SO
 * mata a conexao e o FCM assume.
 */
class RealtimeClient(
    private val tokenStore: SecureTokenStore,
    private val scope: CoroutineScope,
) {

    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile private var running = false
    @Volatile private var socket: WebSocket? = null

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        socket?.close(1000, "app foreground gone")
        socket = null
        _connected.value = false
    }

    fun sendTyping(toUserId: String) {
        socket?.send("""{"type":"typing","to":"$toUserId"}""")
    }

    private fun connect() {
        scope.launch {
            if (!running) return@launch
            val token = tokenStore.accessToken()
            if (token == null) {
                // Sem sessao ainda (ex.: logo apos abrir o app). Tenta de novo mais tarde.
                retryLater(15_000)
                return@launch
            }
            // Token no header, nao na URL: query string pode ficar em log de
            // proxy, metricas de acesso e historico do navegador/app.
            val request = Request.Builder()
                .url(BuildConfig.WS_URL)
                .addHeader("Authorization", "Bearer $token")
                .build()
            socket = client.newWebSocket(request, listener)
        }
    }

    private fun retryLater(millis: Long = 5_000) {
        scope.launch {
            delay(millis)
            if (running) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.value = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when {
                text.contains("\"type\":\"event\"") -> AppEvents.signal(AppEvents.FEED)
                text.contains("\"type\":\"message\"") -> AppEvents.signal(AppEvents.CHAT)
                text.contains("\"type\":\"message_read\"") -> AppEvents.signal(AppEvents.CHAT)
                text.contains("\"type\":\"typing\"") -> AppEvents.signal(AppEvents.CHAT)
                text.contains("\"type\":\"grant\"") -> {
                    AppEvents.signal(AppEvents.GRANTS)
                    AppEvents.signal(AppEvents.FRIENDS)
                }
                text.contains("\"type\":\"presence\"") -> {
                    AppEvents.signal(AppEvents.PRESENCE)
                    AppEvents.signal(AppEvents.FRIENDS)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            _connected.value = false
            if (running) retryLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            _connected.value = false
            if (running) retryLater()
        }
    }
}
