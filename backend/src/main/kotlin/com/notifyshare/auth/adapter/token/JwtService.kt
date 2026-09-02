package com.notifyshare.auth.adapter.token

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Access token: JWT assinado, curto, sem estado no servidor.
 * Refresh token: valor aleatorio opaco. O servidor guarda so o SHA-256 dele.
 *
 * O refresh nao e JWT de proposito: precisa poder ser revogado, e um JWT
 * so seria revogavel com uma lista de bloqueio, ou seja, estado de qualquer jeito.
 */
@Component
class JwtService(private val properties: JwtProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    private val key: SecretKey = run {
        val bytes = properties.secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) {
            "notifyshare.jwt.secret precisa de pelo menos 32 bytes para HS256 (tem ${bytes.size})"
        }
        Keys.hmacShaKeyFor(bytes)
    }

    data class AccessToken(val value: String, val expiresAt: Instant)

    fun issueAccessToken(userId: UUID, nickname: String): AccessToken {
        val now = Instant.now()
        val expiresAt = now.plus(properties.accessTtl)
        val token = Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .claim("nickname", nickname)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return AccessToken(token, expiresAt)
    }

    /** Retorna as claims, ou null se o token for invalido, expirado ou adulterado. */
    fun parseAccessToken(token: String): Claims? = try {
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(properties.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (e: JwtException) {
        log.debug("Access token recusado: {}", e.message)
        null
    } catch (e: IllegalArgumentException) {
        log.debug("Access token malformado: {}", e.message)
        null
    }

    /** 32 bytes aleatorios em base64url. Este valor so viaja e vive no cliente. */
    fun generateRefreshTokenValue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hashRefreshToken(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun refreshExpiry(): Instant = Instant.now().plus(properties.refreshTtl)

    val accessTtlSeconds: Long get() = properties.accessTtl.seconds

    val refreshReuseGrace: java.time.Duration get() = properties.refreshReuseGrace
}
