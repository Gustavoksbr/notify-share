package com.notifyshare.friends.adapter.web

import com.notifyshare.friends.application.FriendService
import com.notifyshare.friends.application.FriendView
import com.notifyshare.friends.application.PendingRequests
import com.notifyshare.friends.application.SearchResult
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class NicknameRequest(
    @field:NotBlank @field:Schema(example = "marina.costa") val nickname: String = "",
    @field:Schema(description = "Ao aceitar a amizade, já criar um compartilhamento oferecendo minhas notificações.")
    val alsoOfferShare: Boolean = false,
    @field:Schema(description = "Ao aceitar a amizade, já pedir para receber as notificações dessa pessoa.")
    val alsoRequestShare: Boolean = false,
)

@RestController
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Amigos", description = "Busca por nickname e pedidos de amizade")
class FriendController(private val friends: FriendService) {

    @GetMapping("/users/search")
    @Operation(
        summary = "Busca pessoas por nickname",
        description = "Prefixo, minimo 2 caracteres. Devolve a relacao atual e amigos em comum.",
    )
    fun search(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam("q") query: String,
    ): List<SearchResult> = friends.search(me.id, query)

    @GetMapping("/friends")
    @Operation(summary = "Lista os amigos aceitos")
    fun list(@AuthenticationPrincipal me: AuthenticatedUser): List<FriendView> =
        friends.listFriends(me.id)

    @GetMapping("/friends/requests")
    @Operation(summary = "Pedidos de amizade pendentes", description = "Recebidos e enviados.")
    fun requests(@AuthenticationPrincipal me: AuthenticatedUser): PendingRequests =
        friends.pending(me.id)

    @PostMapping("/friends/requests")
    @Operation(
        summary = "Envia um pedido de amizade",
        description = "Se a outra pessoa ja tinha pedido, a amizade e aceita na hora.",
    )
    fun request(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: NicknameRequest,
    ): SearchResult = friends.request(me.id, body.nickname, body.alsoOfferShare, body.alsoRequestShare)

    @PostMapping("/friends/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aceita um pedido recebido")
    fun accept(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        friends.respond(me.id, id, accept = true)

    @PostMapping("/friends/requests/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Recusa um pedido recebido")
    fun decline(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        friends.respond(me.id, id, accept = false)

    @DeleteMapping("/friends/{nickname}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desfaz a amizade")
    fun remove(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable nickname: String) =
        friends.remove(me.id, nickname)
}
