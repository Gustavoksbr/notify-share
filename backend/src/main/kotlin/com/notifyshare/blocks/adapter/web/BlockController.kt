package com.notifyshare.blocks.adapter.web

import com.notifyshare.blocks.application.BlockService
import com.notifyshare.blocks.application.BlockedUserView
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class BlockRequest(
    @field:NotBlank @field:Schema(example = "marina.costa") val nickname: String = "",
)

@RestController
@RequestMapping("/blocks")
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Bloqueios", description = "Bloquear e desbloquear pessoas")
class BlockController(private val blocks: BlockService) {

    @GetMapping
    @Operation(summary = "Lista as pessoas que voce bloqueou")
    fun list(@AuthenticationPrincipal me: AuthenticatedUser): List<BlockedUserView> =
        blocks.listBlocked(me.id)

    @PostMapping
    @Operation(summary = "Bloqueia uma pessoa", description = "Idempotente.")
    fun block(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: BlockRequest,
    ): BlockedUserView = blocks.block(me.id, body.nickname)

    @DeleteMapping("/{nickname}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desbloqueia uma pessoa")
    fun unblock(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable nickname: String) =
        blocks.unblock(me.id, nickname)
}
