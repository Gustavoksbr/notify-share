package com.notifyshare.auth.adapter.token

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "notifyshare.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "notify-share",
    val accessTtl: Duration = Duration.ofMinutes(15),
    val refreshTtl: Duration = Duration.ofDays(30),
)
