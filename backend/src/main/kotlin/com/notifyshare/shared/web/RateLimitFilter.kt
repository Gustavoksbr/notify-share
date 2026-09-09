package com.notifyshare.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.notifyshare.auth.application.PasswordResetProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private data class RateLimitRule(
    val method: String,
    val path: String,
    val limit: Int,
    val window: Duration,
    // false = por IP (rotas publicas, sem usuario ainda). true = por usuario
    // autenticado, com fallback pra IP se por algum motivo nao houver principal.
    val keyedByUser: Boolean = false,
)

private class Bucket {
    val windowStart = AtomicLong(System.currentTimeMillis())
    val count = AtomicInteger(0)
}

/**
 * Limite de requisicoes em memoria (janela fixa por IP ou usuario), sem
 * dependencia externa — a API roda numa instancia so no Render, entao Redis
 * seria over-engineering aqui. Cobre as rotas mais visadas para brute force
 * ou automacao: cadastro, login, login Google, refresh e busca de usuarios.
 * Zera se o processo reiniciar; o objetivo e frear automacao, nao ser
 * inviolavel.
 */
@Component
class RateLimitFilter(
    // Desligado nos testes de integracao (@SpringBootTest): eles registram
    // varias contas em sequencia rapida a partir do mesmo IP de loopback, o
    // que e trafego legitimo de teste, nao abuso.
    @param:Value("\${notifyshare.rate-limit.enabled:true}") private val enabled: Boolean = true,
    @param:Value("\${notifyshare.rate-limit.password-reset-window:PT15M}")
    private val passwordResetWindow: Duration = Duration.ofMinutes(15),
    @param:Value("\${notifyshare.rate-limit.login-window:PT1M}")
    private val loginWindow: Duration = Duration.ofMinutes(1),
    // So para o limite de /auth/forgot-password ficar em um lugar so (a tela de
    // recuperacao le esse mesmo numero para explicar ao usuario).
    private val passwordResetProps: PasswordResetProperties = PasswordResetProperties(),
) : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val rules = listOf(
        RateLimitRule("POST", "/auth/register", limit = 5, window = Duration.ofMinutes(1)),
        RateLimitRule("POST", "/auth/login", limit = 10, window = loginWindow),
        RateLimitRule("POST", "/auth/google", limit = 10, window = Duration.ofMinutes(1)),
        RateLimitRule("POST", "/auth/refresh", limit = 20, window = Duration.ofMinutes(1)),
        // Recuperacao de senha: rara (voce faz uma vez). Teto baixo por IP corta
        // quem tenta usar a rota pra floodar a caixa de outra pessoa.
        RateLimitRule(
            "POST", "/auth/forgot-password",
            limit = passwordResetProps.maxRequestsPerWindow, window = passwordResetWindow,
        ),
        RateLimitRule("POST", "/auth/reset-password", limit = 15, window = passwordResetWindow),
        RateLimitRule("GET", "/users/search", limit = 30, window = Duration.ofMinutes(1), keyedByUser = true),
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rule = rules.find { it.method == request.method && it.path == request.requestURI }
        if (!enabled || rule == null) {
            filterChain.doFilter(request, response)
            return
        }

        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        val subject = if (rule.keyedByUser) principal?.id?.toString() else null
        val key = "${rule.method} ${rule.path}:${subject ?: request.remoteAddr}"

        if (!tryConsume(key, rule.limit, rule.window)) {
            val retryAfter = retryAfterSeconds(key, rule.window)
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.setHeader("Retry-After", retryAfter.toString())
            response.writer.write(
                """{"code":"rate_limited","message":"${blockedMessage(rule, retryAfter)}",""" +
                    """"retryAfterSeconds":$retryAfter}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    /** Quanto falta, em segundos, para a janela fixa daquele balde reiniciar. */
    private fun retryAfterSeconds(key: String, window: Duration): Long {
        val bucket = buckets[key] ?: return window.seconds
        val elapsed = System.currentTimeMillis() - bucket.windowStart.get()
        return ((window.toMillis() - elapsed).coerceAtLeast(1_000L) / 1_000L)
    }

    private fun blockedMessage(rule: RateLimitRule, retryAfter: Long): String {
        val quando = humanApprox(retryAfter)
        return when (rule.path) {
            "/auth/forgot-password" ->
                "Você já pediu o código o máximo de vezes (${rule.limit} a cada " +
                    "${humanWindow(rule.window)}). Tente de novo em $quando."
            "/auth/reset-password" ->
                "Muitas tentativas de redefinição. Tente de novo em $quando."
            else -> "Muitas tentativas. Tente de novo em $quando."
        }
    }

    private fun humanApprox(totalSeconds: Long): String {
        val minutes = (totalSeconds + 59) / 60
        return when {
            totalSeconds <= 1L -> "cerca de 1 segundo"
            totalSeconds < 60L -> "cerca de $totalSeconds segundos"
            minutes <= 1L -> "cerca de 1 minuto"
            else -> "cerca de $minutes minutos"
        }
    }

    private fun humanWindow(window: Duration): String {
        val minutes = window.toMinutes()
        return when {
            minutes == 1L -> "1 minuto"
            minutes > 1L -> "$minutes minutos"
            else -> "${window.seconds} segundos"
        }
    }

    private fun tryConsume(key: String, limit: Int, window: Duration): Boolean {
        val bucket = buckets.computeIfAbsent(key) { Bucket() }
        synchronized(bucket) {
            val now = System.currentTimeMillis()
            if (now - bucket.windowStart.get() >= window.toMillis()) {
                bucket.windowStart.set(now)
                bucket.count.set(0)
            }
            return bucket.count.incrementAndGet() <= limit
        }
    }
}
