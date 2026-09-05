package com.notifyshare.auth.application

import com.notifyshare.shared.web.TooManyRequestsException
import com.notifyshare.shared.web.UnauthorizedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private class LoginState {
    val failures = AtomicInteger(0)
    val lockedUntil = AtomicLong(0)
}

/**
 * Bloqueio temporario por senha errada, por identificador (nickname ou
 * e-mail como foi digitado — o mesmo que AuthService.login normaliza). Em
 * memoria, sem dependencia externa, pelo mesmo motivo do RateLimitFilter: uma
 * instancia so no Render. Zera se o processo reiniciar.
 *
 * Configuravel via .env: LOGIN_MAX_ATTEMPTS (quantas tentativas erradas ate
 * bloquear, padrao 11) e LOGIN_LOCKOUT_DURATION (quanto tempo de bloqueio,
 * formato ISO-8601 tipo PT1M, padrao 1 minuto — curto de proposito, e so
 * mudar o .env se sentir necessidade depois).
 */
@Component
class LoginAttemptGuard(
    @param:Value("\${notifyshare.auth.login-max-attempts:11}") private val maxAttempts: Int,
    @param:Value("\${notifyshare.auth.login-lockout-duration:PT1M}") private val lockoutDuration: Duration,
    // Desligado nos testes de integracao: eles reusam identificadores entre
    // varios "senha errada" de proposito (ex.: testar que erro de senha e de
    // usuario inexistente sao identicos), o que sozinho ja bateria o limite.
    @param:Value("\${notifyshare.auth.login-lockout-enabled:true}") private val enabled: Boolean = true,
) {

    private val states = ConcurrentHashMap<String, LoginState>()

    /** Chamar antes de checar a senha. Lanca se o identificador estiver bloqueado agora. */
    fun assertNotLocked(identifier: String) {
        if (!enabled) return
        val state = states[identifier] ?: return
        val remainingMs = state.lockedUntil.get() - System.currentTimeMillis()
        if (remainingMs > 0) throw lockedException(remainingMs)
    }

    /**
     * Chamar quando a senha (ou o usuario) deu errado. Sempre lanca: ou o erro
     * normal de credenciais (com quantas tentativas restam), ou — se essa foi
     * a que estourou o limite — o erro de bloqueio.
     */
    fun recordFailure(identifier: String): Nothing {
        if (!enabled) {
            throw UnauthorizedException("invalid_credentials", "Nickname ou senha incorretos")
        }
        val state = states.computeIfAbsent(identifier) { LoginState() }
        val count = state.failures.incrementAndGet()
        if (count >= maxAttempts) {
            state.failures.set(0)
            state.lockedUntil.set(System.currentTimeMillis() + lockoutDuration.toMillis())
            throw lockedException(lockoutDuration.toMillis())
        }
        throw UnauthorizedException(
            "invalid_credentials",
            "Nickname ou senha incorretos",
            attemptsRemaining = maxAttempts - count,
        )
    }

    /** Chamar em todo login bem-sucedido: zera o historico daquele identificador. */
    fun recordSuccess(identifier: String) {
        states.remove(identifier)
    }

    private fun lockedException(remainingMs: Long): TooManyRequestsException {
        val seconds = Duration.ofMillis(remainingMs).seconds.coerceAtLeast(1)
        return TooManyRequestsException(
            "account_locked",
            "Muitas tentativas. Tente novamente em ${humanDuration(seconds)}.",
            retryAfterSeconds = seconds,
        )
    }

    private fun humanDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> "${minutes}min ${seconds}s"
            minutes == 1L -> "1 minuto"
            minutes > 1L -> "$minutes minutos"
            seconds == 1L -> "1 segundo"
            else -> "$seconds segundos"
        }
    }
}
