package com.notifyshare.auth.adapter.web

import com.notifyshare.auth.application.AuthService
import com.notifyshare.auth.application.IssuedTokens
import com.notifyshare.auth.application.PasswordResetProperties
import com.notifyshare.auth.application.PasswordResetService
import org.springframework.beans.factory.annotation.Value
import java.time.Duration
import com.notifyshare.auth.domain.User
import jakarta.servlet.http.HttpServletRequest
import com.notifyshare.shared.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.notifyshare.shared.web.AuthenticatedUser
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

    @field:Schema(description = "Aceite da Politica de Privacidade. Obrigatorio.", example = "true")
    val acceptedPrivacy: Boolean = false,

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

@Schema(description = "Login ou cadastro com uma conta Google")
data class GoogleLoginRequest(
    @field:NotBlank
    @field:Schema(description = "ID token do Google obtido no app (Credential Manager).")
    val idToken: String = "",

    @field:Schema(
        description = "So no primeiro login dessa conta Google: o nickname escolhido. " +
            "Se a conta ainda nao existe e vier vazio, a resposta e 409 needs_nickname.",
        example = "gustavoksbr",
    )
    val nickname: String? = null,

    @field:Schema(description = "Aceite da Politica de Privacidade. Obrigatorio ao criar conta nova.", example = "true")
    val acceptedPrivacy: Boolean = false,

    @field:Schema(example = "Redmi 12C")
    val deviceLabel: String? = null,
)

data class LogoutRequest(
    @field:NotBlank
    @field:Schema(description = "O refreshToken da sessao que voce quer encerrar.")
    val refreshToken: String = "",
)

@Schema(description = "Pede um codigo de recuperacao de senha por e-mail")
data class ForgotPasswordRequest(
    @field:NotBlank(message = "Informe seu e-mail")
    @field:Schema(example = "voce@exemplo.com")
    val email: String = "",
)

@Schema(description = "Redefine a senha com o codigo recebido por e-mail")
data class ResetPasswordRequest(
    @field:NotBlank(message = "Informe seu e-mail")
    @field:Schema(example = "voce@exemplo.com")
    val email: String = "",

    @field:NotBlank(message = "Informe o codigo")
    @field:Schema(description = "Os 6 digitos enviados por e-mail.", example = "482913")
    val code: String = "",

    @field:NotBlank(message = "Informe a senha nova")
    @field:Schema(description = "Minimo de 8 caracteres.", example = "senha-nova-123")
    val newPassword: String = "",
)

@Schema(description = "Limites da recuperacao de senha, para a tela poder explica-los antes do erro")
data class PasswordResetInfoResponse(
    @field:Schema(description = "Quantos codigos errados o mesmo IP pode tentar antes do bloqueio.")
    val maxCodeAttempts: Int,
    @field:Schema(description = "Duracao do bloqueio, em minutos, ao esgotar as tentativas.")
    val lockoutMinutes: Long,
    @field:Schema(description = "Quantos pedidos de codigo o mesmo IP pode fazer na janela.")
    val maxRequests: Int,
    @field:Schema(description = "Tamanho da janela dos pedidos, em minutos.")
    val requestWindowMinutes: Long,
    @field:Schema(description = "Validade do codigo enviado por e-mail, em minutos.")
    val codeTtlMinutes: Long,
    @field:Schema(description = "Intervalo minimo entre dois envios para o mesmo e-mail, em segundos.")
    val resendCooldownSeconds: Long,
)

private fun ceilMinutes(d: Duration): Long = (d.seconds + 59) / 60

@Schema(description = "Perfil do proprio usuario. O e-mail so aparece aqui.")
data class MeResponse(
    val id: UUID,
    @field:Schema(example = "gustavoksbr") val nickname: String,
    @field:Schema(example = "voce@exemplo.com") val email: String,
    val createdAt: Instant,
    @field:Schema(description = "Conta vinculada ao login com Google.") val google: Boolean = false,
    @field:Schema(description = "Conta tem senha (da para entrar sem o Google).") val hasPassword: Boolean = true,
    @field:Schema(description = "Ja aceitou a Politica de Privacidade.") val privacyAccepted: Boolean = true,
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

private fun User.toMe() = MeResponse(
    id = id,
    nickname = nickname,
    email = email,
    createdAt = createdAt,
    google = googleSub != null,
    hasPassword = passwordHash != null,
    privacyAccepted = privacyAcceptedAt != null,
)

private fun IssuedTokens.toResponse() =
    TokenResponse(accessToken, refreshToken, expiresInSeconds, user.toMe())

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacao", description = "Cadastro, login e ciclo de vida das sessoes")
class AuthController(
    private val authService: AuthService,
    private val passwordReset: PasswordResetService,
    private val passwordResetProps: PasswordResetProperties,
    @param:Value("\${notifyshare.rate-limit.password-reset-window:PT15M}")
    private val passwordResetWindow: Duration,
) {

    @GetMapping("/password-reset-info")
    @Operation(
        summary = "Limites da recuperacao de senha",
        description = "Publico. A tela usa isto para dizer de antemao quantos codigos podem " +
            "ser pedidos, por quanto tempo o erro bloqueia e qual a janela.",
    )
    fun passwordResetInfo() = PasswordResetInfoResponse(
        maxCodeAttempts = passwordResetProps.maxAttempts,
        lockoutMinutes = ceilMinutes(passwordResetProps.lockoutDuration),
        maxRequests = passwordResetProps.maxRequestsPerWindow,
        requestWindowMinutes = ceilMinutes(passwordResetWindow),
        codeTtlMinutes = ceilMinutes(passwordResetProps.codeTtl),
        resendCooldownSeconds = passwordResetProps.sendCooldown.seconds,
    )

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
        authService.register(
            body.nickname, body.email, body.password, body.deviceLabel, body.acceptedPrivacy,
        ).toResponse()

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

    @PostMapping("/google")
    @Operation(
        summary = "Entra ou cadastra com uma conta Google",
        description = "Valida o ID token com o Google. Se for a primeira vez dessa conta e nao " +
            "vier `nickname`, devolve 409 com code=needs_nickname e o e-mail em `message` — " +
            "o app pergunta o nickname e repete a chamada.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Autenticado"),
        ApiResponse(responseCode = "401", description = "code=invalid_google_token, google_audience_mismatch..."),
        ApiResponse(responseCode = "409", description = "code=needs_nickname (message=e-mail) ou nickname_taken"),
    )
    fun google(@Valid @RequestBody body: GoogleLoginRequest): TokenResponse =
        authService.loginWithGoogle(
            body.idToken, body.nickname, body.deviceLabel, body.acceptedPrivacy,
        ).toResponse()

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

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Pede um codigo de recuperacao de senha",
        description = "Responde 202 sempre, exista a conta ou nao — a API nao revela quais " +
            "e-mails tem cadastro. Se existir e tiver senha, um codigo de 6 digitos vai por e-mail.",
    )
    fun forgotPassword(@Valid @RequestBody body: ForgotPasswordRequest) {
        passwordReset.forgot(body.email)
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Redefine a senha com o codigo do e-mail",
        description = "Codigo errado/expirado -> 401 invalid_reset_code (com attemptsRemaining). " +
            "Muitos erros do mesmo IP -> 429 reset_locked. Ao redefinir, todas as sessoes caem.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Senha redefinida"),
        ApiResponse(responseCode = "400", description = "code=weak_password"),
        ApiResponse(responseCode = "401", description = "code=invalid_reset_code"),
        ApiResponse(responseCode = "429", description = "code=reset_locked"),
    )
    fun resetPassword(@Valid @RequestBody body: ResetPasswordRequest, request: HttpServletRequest) {
        passwordReset.reset(body.email, body.code, body.newPassword, request.remoteAddr)
    }

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
