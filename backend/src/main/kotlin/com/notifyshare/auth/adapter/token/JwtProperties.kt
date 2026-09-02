package com.notifyshare.auth.adapter.token

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "notifyshare.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "notify-share",
    val accessTtl: Duration = Duration.ofMinutes(15),
    val refreshTtl: Duration = Duration.ofDays(90),
    /**
     * Janela em que reapresentar um refresh token recem-rotacionado e tratado
     * como corrida do proprio cliente (duas chamadas tomaram 401 juntas), e nao
     * como vazamento. Fora dela, ou se a rotacao seguinte ja aconteceu, o reuso
     * derruba a familia.
     */
    val refreshReuseGrace: Duration = Duration.ofSeconds(60),
)
