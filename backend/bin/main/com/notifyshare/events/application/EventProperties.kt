package com.notifyshare.events.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "notifyshare.events")
data class EventProperties(
    /** Historico apagado depois deste tempo. O usuario pode encurtar no app (Milestone D). */
    val defaultRetention: Duration = Duration.ofDays(7),
)
