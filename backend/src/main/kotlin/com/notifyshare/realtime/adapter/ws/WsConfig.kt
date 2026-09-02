package com.notifyshare.realtime.adapter.ws

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WsConfig(
    private val gateway: WebSocketGateway,
    private val handshake: WsHandshakeInterceptor,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(gateway, "/ws")
            .addInterceptors(handshake)
            // O app nao e um browser; nao ha origem que precise casar.
            .setAllowedOriginPatterns("*")
    }
}
