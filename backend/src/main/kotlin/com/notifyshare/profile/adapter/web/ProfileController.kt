package com.notifyshare.profile.adapter.web

import com.notifyshare.profile.application.ProfileService
import com.notifyshare.profile.application.UserProfileView
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Perfil", description = "Perfil publico de outra pessoa")
class ProfileController(private val profile: ProfileService) {

    @GetMapping("/users/{nickname}/profile")
    @Operation(
        summary = "Perfil de outra pessoa",
        description = "Nickname, se sao amigos, se voce bloqueou, e os grants entre voces.",
    )
    fun profile(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable nickname: String,
    ): UserProfileView = profile.profileOf(me.id, nickname)
}
