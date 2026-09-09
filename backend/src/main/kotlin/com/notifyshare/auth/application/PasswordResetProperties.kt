package com.notifyshare.auth.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuracao da recuperacao de senha. Espelha o bloqueio do login: da pra
 * ajustar quantos codigos errados ate travar e por quanto tempo, tudo via .env.
 *
 * O bloqueio de "codigo errado" e por IP, nao por e-mail, de proposito: se
 * fosse por e-mail, qualquer um que saiba o seu endereco poderia travar a sua
 * recuperacao errando o codigo de proposito. Por IP, quem erra e o proprio
 * atacante. (O login normal continua funcionando de qualquer forma — bloquear
 * o reset nao bloqueia a conta.)
 */
@ConfigurationProperties(prefix = "notifyshare.auth.password-reset")
data class PasswordResetProperties(
    /** Codigos errados (por IP) ate o bloqueio temporario. PASSWORD_RESET_MAX_ATTEMPTS. */
    val maxAttempts: Int = 5,
    /** Duracao do bloqueio. PASSWORD_RESET_LOCKOUT_DURATION, ISO-8601 (PT5M). */
    val lockoutDuration: Duration = Duration.ofMinutes(5),
    /** Validade do codigo enviado por e-mail. PASSWORD_RESET_CODE_TTL. */
    val codeTtl: Duration = Duration.ofMinutes(20),
    /** Tempo minimo entre dois e-mails para o MESMO endereco (anti-flood). */
    val sendCooldown: Duration = Duration.ofSeconds(60),
    /**
     * Quantos pedidos de codigo (POST /auth/forgot-password) o mesmo IP pode
     * fazer dentro da janela do RateLimitFilter. So espelha o limite do filtro
     * aqui para a tela poder explica-lo ao usuario. PASSWORD_RESET_MAX_REQUESTS.
     */
    val maxRequestsPerWindow: Int = 4,
    /**
     * Desligado nos testes de integracao (varios "codigo errado" seguidos do
     * mesmo loopback batem o teto sozinhos).
     */
    val lockoutEnabled: Boolean = true,
)

/**
 * Credenciais do Resend (envio de e-mail transacional). Vazio = envio
 * desligado: em dev o codigo vai pro log em vez de ser enviado.
 */
@ConfigurationProperties(prefix = "notifyshare.resend")
data class ResendProperties(
    val apiKey: String = "",
    /** Remetente verificado no Resend, ex.: "Notify Share <nao-responda@seudominio.com>". */
    val from: String = "",
) {
    val enabled: Boolean get() = apiKey.isNotBlank() && from.isNotBlank()
}
