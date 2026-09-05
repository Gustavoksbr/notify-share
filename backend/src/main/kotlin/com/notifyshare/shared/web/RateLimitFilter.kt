package com.notifyshare.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
) : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private val rules = listOf(
        RateLimitRule("POST", "/auth/register", limit = 5, window = Duration.ofMinutes(1)),
        RateLimitRule("POST", "/auth/login", limit = 10, window = Duration.ofMinutes(1)),
        RateLimitRule("POST", "/auth/google", limit = 10, window = Duration.ofMinutes(1)),
        RateLimitRule("POST", "/auth/refresh", limit = 20, window = Duration.ofMinutes(1)),
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
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(
                """{"code":"rate_limited","message":"Muitas tentativas. Aguarde um pouco e tente de novo."}"""
            )
            return
        }

        filterChain.doFilter(request, response)
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
