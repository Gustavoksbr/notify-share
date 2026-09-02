package com.notifyshare.realtime.adapter.web

import com.notifyshare.realtime.application.Presence
import com.notifyshare.realtime.application.PresenceService
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Presenca", description = "Quem esta online agora")
class PresenceController(private val presence: PresenceService) {

    @GetMapping("/presence")
    @Operation(
        summary = "Presenca de varias pessoas",
        description = "users=nick1,nick2. online=true, ou lastSeen com o ultimo heartbeat.",
    )
    fun presence(
        @Suppress("unused") @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam users: List<String>,
    ): List<Presence> = presence.forNicknames(users)
}
