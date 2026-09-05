package com.notifyshare.account

import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Conta", description = "Portabilidade e exclusao de dados (LGPD)")
@SecurityRequirement(name = BEARER_SCHEME)
class AccountController(private val account: AccountService) {

    @GetMapping("/me/export")
    @Operation(summary = "Baixa todos os seus dados em JSON")
    fun export(@AuthenticationPrincipal me: AuthenticatedUser): Map<String, Any?> =
        account.export(me.id)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Exclui a conta e todos os dados",
        description = "Irreversivel. Apaga amizades, compartilhamentos, regras, mensagens e histórico.",
    )
    fun delete(@AuthenticationPrincipal me: AuthenticatedUser) = account.delete(me.id)
}
