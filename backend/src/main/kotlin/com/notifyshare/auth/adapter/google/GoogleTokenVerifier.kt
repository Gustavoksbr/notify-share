package com.notifyshare.auth.adapter.google

import com.notifyshare.shared.web.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class GoogleIdentity(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
)

/**
 * Valida o ID token do Google pelo endpoint oficial de tokeninfo. Uma chamada
 * HTTP por login — o Google confere assinatura, expiracao e emissor, e devolve
 * as claims. Simples e sem dependencia; a nossa escala nao precisa de validacao
 * offline com JWKS.
 */
@Component
class GoogleTokenVerifier(private val properties: GoogleProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun verify(idToken: String): GoogleIdentity {
        if (!properties.enabled) {
            throw UnauthorizedException("google_disabled", "Login com Google nao esta configurado")
        }

        val encoded = URLEncoder.encode(idToken, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=$encoded"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("falha ao falar com o tokeninfo do Google: {}", e.message)
            throw UnauthorizedException("google_unreachable", "Nao foi possivel validar com o Google agora")
        }

        if (response.statusCode() != 200) {
            throw UnauthorizedException("invalid_google_token", "Token do Google invalido ou expirado")
        }

        val body = response.body()
        val aud = field(body, "aud")
        if (aud != properties.clientId) {
            log.warn("aud do token nao bate: {}", aud)
            throw UnauthorizedException("google_audience_mismatch", "Esse token nao e para este app")
        }

        val sub = field(body, "sub")
            ?: throw UnauthorizedException("invalid_google_token", "Token do Google sem 'sub'")
        val email = field(body, "email")
            ?: throw UnauthorizedException("google_no_email", "A conta Google nao expos um e-mail")

        return GoogleIdentity(
            sub = sub,
            email = email,
            emailVerified = field(body, "email_verified") == "true",
            name = field(body, "name"),
        )
    }

    private fun field(json: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
}
