package com.notifyshare.realtime.adapter.ws

import com.notifyshare.realtime.application.PresenceChanged
import com.notifyshare.realtime.application.RealtimePort
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Registro de sessoes WebSocket vivas, por usuario. Uma so instancia do
 * servidor por enquanto; escala horizontal depois exige um pub/sub compartilhado
 * (Redis) — o resto do codigo nao muda porque so fala com a porta.
 */
@Component
class WebSocketGateway(
    private val events: ApplicationEventPublisher,
) : TextWebSocketHandler(), RealtimePort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.userId
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val set = sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
        val wasOffline = set.isEmpty()
        set.add(session)
        log.debug("ws conectado: {} ({} sessoes)", userId, set.size)
        if (wasOffline) events.publishEvent(PresenceChanged(userId, online = true))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.userId ?: return
        sessions[userId]?.let { set ->
            set.remove(session)
            if (set.isEmpty()) {
                sessions.remove(userId)
                events.publishEvent(PresenceChanged(userId, online = false))
            }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val from = session.userId ?: return
        val payload = message.payload
        when {
            // manter viva; o cliente pinga a cada ~30s
            payload.contains("\"type\":\"ping\"") ->
                runCatching { session.sendMessage(TextMessage("{\"type\":\"pong\"}")) }

            // "digitando": repassa para o destinatario, sem persistir
            payload.contains("\"type\":\"typing\"") -> {
                val to = TO_REGEX.find(payload)?.groupValues?.get(1)?.let(::runCatchingUuid) ?: return
                push(to, "typing", mapOf("from" to from.toString()))
            }
        }
    }

    // --- RealtimePort ---------------------------------------------------

    override fun push(userId: UUID, type: String, data: Map<String, String>) {
        val set = sessions[userId] ?: return
        val frame = TextMessage(jsonObject(mapOf("type" to type) + data))
        set.forEach { s ->
            runCatching { if (s.isOpen) synchronized(s) { s.sendMessage(frame) } }
                .onFailure { log.debug("falha ao enviar frame ws: {}", it.message) }
        }
    }

    override fun isOnline(userId: UUID): Boolean = sessions.containsKey(userId)

    override fun onlineAmong(userIds: Collection<UUID>): Set<UUID> =
        userIds.filterTo(HashSet()) { sessions.containsKey(it) }

    // --- util ----------------------------------------------------------

    private val WebSocketSession.userId: UUID?
        get() = attributes[USER_ID_ATTR] as? UUID

    private fun runCatchingUuid(s: String): UUID? = runCatching { UUID.fromString(s) }.getOrNull()

    /** JSON manual: os frames sao Map<String,String>, entao nao vale trazer o mapper aqui. */
    private fun jsonObject(map: Map<String, String>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"${escape(v)}\"" }

    private fun escape(s: String): String = buildString(s.length + 8) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    companion object {
        const val USER_ID_ATTR = "userId"
        private val TO_REGEX = Regex("\"to\"\\s*:\\s*\"([^\"]+)\"")
    }
}
