package com.notifyshare.realtime.adapter.ws

import com.notifyshare.auth.adapter.token.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

private const val BEARER = "Bearer "

/**
 * Autentica o handshake do WebSocket pelo header Authorization (igual as
 * chamadas HTTP normais). Nao usa query string: URL pode ficar em log de
 * proxy, metricas de acesso e historico. Sem token valido, o upgrade e
 * recusado com 401 e a conexao nem chega ao handler.
 */
@Component
class WsHandshakeInterceptor(private val jwt: JwtService) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val header = request.headers.getFirst("Authorization")
        val token = header?.takeIf { it.startsWith(BEARER) }?.substring(BEARER.length)
        val claims = token?.let { jwt.parseAccessToken(it) }
        if (claims == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        attributes[WebSocketGateway.USER_ID_ATTR] = UUID.fromString(claims.subject)
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit
}
