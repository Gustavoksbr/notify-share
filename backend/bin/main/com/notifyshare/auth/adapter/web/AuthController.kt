package com.notifyshare.auth.adapter.web

import com.notifyshare.auth.application.AuthService
import com.notifyshare.auth.application.IssuedTokens
import com.notifyshare.auth.domain.User
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(description = "Dados para criar uma conta")
data class RegisterRequest(
    @field:NotBlank(message = "Informe um nickname")
    @field:Schema(
        description = "Identificador publico. Letras, numeros, ponto e underscore. E gravado em minusculas.",
        example = "gustavoksbr",
    )
    val nickname: String = "",

    @field:NotBlank(message = "Informe um e-mail")
    @field:Schema(
        description = "Privado e unico. Nao e verificado nesta versao e nao recebe nenhuma mensagem.",
        example = "voce@exemplo.com",
    )
    val email: String = "",

    @field:NotBlank(message = "Informe uma senha")
    @field:Schema(description = "Minimo de 8 caracteres.", example = "senha-forte-123")
    val password: String = "",

    @field:Schema(
        description = "Rotulo do aparelho, so para voce reconhecer a sessao depois.",
        example = "Redmi 12C",
    )
    val deviceLabel: String? = null,
)

@Schema(description = "Credenciais de acesso")
data class LoginRequest(
    @field:NotBlank(message = "Informe seu nickname ou e-mail")
    @field:Schema(description = "Aceita nickname ou e-mail.", example = "gustavoksbr")
    val identifier: String = "",

    @field:NotBlank(message = "Informe sua senha")
    @field:Schema(example = "senha-forte-123")
    val password: String = "",

    @field:Schema(example = "Redmi 12C")
    val deviceLabel: String? = null,
)

data class RefreshRequest(
    @field:NotBlank
    @field:Schema(description = "O refreshToken devolvido no login ou no refresh anterior.")
    val refreshToken: String = "",

    @field:Schema(example = "Redmi 12C")
    val deviceLabel: String? = null,
)

data class LogoutRequest(
    @field:NotBlank
    @field:Schema(description = "O refreshToken da sessao que voce quer encerrar.")
    val refreshToken: String = "",
)

@Schema(description = "Perfil do proprio usuario. O e-mail so aparece aqui.")
data class MeResponse(
    val id: UUID,
    @field:Schema(example = "gustavoksbr") val nickname: String,
    @field:Schema(example = "voce@exemplo.com") val email: String,
    val createdAt: Instant,
)

@Schema(description = "Par de tokens emitido")
data class TokenResponse(
    @field:Schema(description = "JWT curto. Vai no header Authorization das rotas autenticadas.")
    val accessToken: String,

    @field:Schema(description = "Valor opaco de 30 dias. Cada uso invalida o anterior.")
    val refreshToken: String,

    @field:Schema(description = "Segundos ate o accessToken expirar.", example = "900")
    val expiresIn: Long,

    val user: MeResponse,
)

private fun User.toMe() = MeResponse(id, nickname, email, createdAt)

private fun IssuedTokens.toResponse() =
    TokenResponse(accessToken, refreshToken, expiresInSeconds, user.toMe())

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacao", description = "Cadastro, login e ciclo de vida das sessoes")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Cria uma conta e ja devolve os tokens",
        description = "Nickname e e-mail sao normalizados para minusculas. " +
            "Nao ha verificacao de e-mail nem recuperacao de senha nesta versao.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Conta criada"),
        ApiResponse(
            responseCode = "400",
            description = "code=invalid_nickname, invalid_email ou weak_password",
        ),
        ApiResponse(responseCode = "409", description = "code=nickname_taken ou email_taken"),
    )
    fun register(@Valid @RequestBody body: RegisterRequest): TokenResponse =
        authService.register(body.nickname, body.email, body.password, body.deviceLabel).toResponse()

    @PostMapping("/login")
    @Operation(
        summary = "Entra com nickname ou e-mail",
        description = "Senha errada e usuario inexistente devolvem exatamente a mesma resposta, " +
            "de proposito: a API nao pode virar um oraculo de quais nicknames existem.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Autenticado"),
        ApiResponse(responseCode = "401", description = "code=invalid_credentials"),
    )
    fun login(@Valid @RequestBody body: LoginRequest): TokenResponse =
        authService.login(body.identifier, body.password, body.deviceLabel).toResponse()

    @PostMapping("/refresh")
    @Operation(
        summary = "Troca o refreshToken por um par novo",
        description = "O token enviado e invalidado e um novo e emitido no lugar. " +
            "Reenviar um refreshToken ja usado e tratado como vazamento: todas as sessoes " +
            "daquele login sao derrubadas de uma vez (code=refresh_token_reused).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Par novo emitido"),
        ApiResponse(
            responseCode = "401",
            description = "code=invalid_refresh_token ou refresh_token_reused",
        ),
    )
    fun refresh(@Valid @RequestBody body: RefreshRequest): TokenResponse =
        authService.refresh(body.refreshToken, body.deviceLabel).toResponse()

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Encerra a sessao de um aparelho",
        description = "Nao exige accessToken: apresentar o refreshToken ja e prova de posse. " +
            "Se exigisse, voce nao conseguiria sair depois que o accessToken expirasse.",
    )
    fun logout(@Valid @RequestBody body: LogoutRequest) =
        authService.logout(body.refreshToken)

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = BEARER_SCHEME)
    @Operation(
        summary = "Encerra todas as sessoes da conta",
        description = "Exige accessToken, diferente do /logout: derrubar todos os aparelhos " +
            "e uma acao sobre a conta inteira, nao sobre uma sessao.",
    )
    fun logoutEverywhere(@AuthenticationPrincipal principal: AuthenticatedUser) =
        authService.logoutEverywhere(principal.id)
}

@RestController
@Tag(name = "Perfil", description = "Dados do usuario autenticado")
class MeController(private val authService: AuthService) {

    @GetMapping("/me")
    @SecurityRequirement(name = BEARER_SCHEME)
    @Operation(summary = "Perfil de quem esta autenticado")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Perfil"),
        ApiResponse(responseCode = "401", description = "code=unauthenticated"),
    )
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): MeResponse =
        authService.findById(principal.id).toMe()
}
