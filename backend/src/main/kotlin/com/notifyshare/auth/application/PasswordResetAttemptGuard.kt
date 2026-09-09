package com.notifyshare.auth.application

import com.notifyshare.shared.web.TooManyRequestsException
import com.notifyshare.shared.web.UnauthorizedException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private class ResetIpState {
    val failures = AtomicInteger(0)
    val lockedUntil = AtomicLong(0)
}

/**
 * Bloqueio temporario da recuperacao de senha por CODIGO errado, chaveado por
 * IP (ver PasswordResetProperties para o porque). Em memoria, uma instancia so
 * no Render — mesmo padrao do LoginAttemptGuard e do RateLimitFilter.
 */
@Component
class PasswordResetAttemptGuard(private val props: PasswordResetProperties) {

    private val states = ConcurrentHashMap<String, ResetIpState>()

    /** Antes de checar o codigo. Lanca se o IP estiver bloqueado agora. */
    fun assertNotLocked(ip: String) {
        if (!props.lockoutEnabled) return
        val state = states[ip] ?: return
        val remainingMs = state.lockedUntil.get() - System.currentTimeMillis()
        if (remainingMs > 0) throw lockedException(remainingMs)
    }

    /**
     * Codigo errado (ou expirado). Sempre lanca: ou o erro normal com quantas
     * tentativas restam, ou o de bloqueio se essa foi a que estourou.
     */
    fun recordFailure(ip: String): Nothing {
        if (!props.lockoutEnabled) throw invalidCode(null)
        val state = states.computeIfAbsent(ip) { ResetIpState() }
        val count = state.failures.incrementAndGet()
        if (count >= props.maxAttempts) {
            state.failures.set(0)
            state.lockedUntil.set(System.currentTimeMillis() + props.lockoutDuration.toMillis())
            throw lockedException(props.lockoutDuration.toMillis())
        }
        throw invalidCode(props.maxAttempts - count)
    }

    /** Reset concluido: limpa o historico daquele IP. */
    fun recordSuccess(ip: String) {
        states.remove(ip)
    }

    private fun invalidCode(attemptsRemaining: Int?): UnauthorizedException {
        val base = "Código inválido ou expirado."
        val message = if (attemptsRemaining == null) {
            base
        } else {
            val tentativas =
                if (attemptsRemaining == 1) "mais 1 tentativa" else "mais $attemptsRemaining tentativas"
            "$base Você tem $tentativas antes de a recuperação ficar bloqueada por " +
                "${humanApprox(props.lockoutDuration.seconds)}. " +
                "Isso não bloqueia o login com a sua senha atual."
        }
        return UnauthorizedException("invalid_reset_code", message, attemptsRemaining = attemptsRemaining)
    }

    private fun lockedException(remainingMs: Long): TooManyRequestsException {
        val seconds = Duration.ofMillis(remainingMs).seconds.coerceAtLeast(1)
        return TooManyRequestsException(
            "reset_locked",
            "Você errou o código ${props.maxAttempts} vezes. A recuperação está bloqueada; " +
                "tente de novo em ${humanApprox(seconds)}. " +
                "O login com a sua senha atual continua funcionando.",
            retryAfterSeconds = seconds,
        )
    }

    private fun humanApprox(totalSeconds: Long): String {
        val minutes = (totalSeconds + 59) / 60
        return if (minutes <= 1L) "cerca de 1 minuto" else "cerca de $minutes minutos"
    }
}
