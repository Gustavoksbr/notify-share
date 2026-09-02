package com.notifyshare.grants.adapter.web

import com.notifyshare.grants.application.GrantRuleService
import com.notifyshare.grants.application.GrantService
import com.notifyshare.grants.application.GrantView
import com.notifyshare.grants.application.PendingGrants
import com.notifyshare.grants.application.RuleInput
import com.notifyshare.grants.application.RuleView
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import com.notifyshare.shared.web.ValidationException
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GrantTargetRequest(
    @field:NotBlank @field:Schema(example = "mariana") val nickname: String = "",
)

data class RulesRequest(val rules: List<RuleInput> = emptyList())

@RestController
@RequestMapping("/grants")
@SecurityRequirement(name = BEARER_SCHEME)
@Tag(name = "Compartilhamentos", description = "Grants, caixa de pedidos e regras por app")
class GrantController(
    private val grants: GrantService,
    private val ruleService: GrantRuleService,
) {

    @GetMapping
    @Operation(
        summary = "Lista compartilhamentos ativos ou pausados",
        description = "role=sharer devolve 'compartilho com'; role=recipient devolve 'recebo de'.",
    )
    fun list(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(defaultValue = "sharer") role: String,
    ): List<GrantView> = when (role) {
        "sharer" -> grants.listForSharer(me.id)
        "recipient" -> grants.listForRecipient(me.id)
        else -> throw ValidationException("invalid_role", "role deve ser sharer ou recipient", "role")
    }

    @GetMapping("/pending")
    @Operation(summary = "Caixa de pedidos", description = "Recebidos (esperando voce) e enviados.")
    fun pending(@AuthenticationPrincipal me: AuthenticatedUser): PendingGrants =
        grants.pending(me.id)

    @PostMapping("/offers")
    @Operation(
        summary = "Oferece compartilhar com alguem",
        description = "Se a pessoa ja tinha pedido para receber de voce, ativa na hora.",
    )
    fun offer(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: GrantTargetRequest,
    ): GrantView = grants.offer(me.id, body.nickname)

    @PostMapping("/requests")
    @Operation(
        summary = "Pede para receber de alguem",
        description = "Se a pessoa ja tinha oferecido, ativa na hora.",
    )
    fun request(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody body: GrantTargetRequest,
    ): GrantView = grants.request(me.id, body.nickname)

    @PostMapping("/{id}/accept")
    @Operation(summary = "Aceita um pedido pendente")
    fun accept(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID): GrantView =
        grants.accept(me.id, id)

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Recusa um pedido pendente")
    fun decline(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        grants.decline(me.id, id)

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pausa um compartilhamento ativo")
    fun pause(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID): GrantView =
        grants.pause(me.id, id)

    @PostMapping("/{id}/resume")
    @Operation(summary = "Retoma um compartilhamento que voce pausou")
    fun resume(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID): GrantView =
        grants.resume(me.id, id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra o compartilhamento", description = "Apaga as regras junto.")
    fun revoke(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID) =
        grants.revoke(me.id, id)

    // --- regras ---------------------------------------------------------

    @GetMapping("/{id}/rules")
    @Operation(summary = "Regras por app deste grant")
    fun rules(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable id: UUID): List<RuleView> =
        ruleService.get(me.id, id)

    @PutMapping("/{id}/rules")
    @Operation(
        summary = "Substitui as regras deste grant",
        description = "So quem compartilha edita. A lista enviada passa a ser a lista inteira.",
    )
    fun replaceRules(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable id: UUID,
        @RequestBody body: RulesRequest,
    ): List<RuleView> = ruleService.replace(me.id, id, body.rules)
}
