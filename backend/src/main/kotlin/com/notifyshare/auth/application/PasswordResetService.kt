package com.notifyshare.auth.application

import com.notifyshare.auth.adapter.persistence.PasswordResetTokenRepository
import com.notifyshare.auth.adapter.persistence.RefreshTokenRepository
import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.auth.domain.PasswordResetToken
import com.notifyshare.shared.web.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Recuperacao de senha por codigo de 6 digitos no e-mail.
 *
 * `forgot`  -> se o e-mail tem conta com senha, cria um codigo e manda por
 *              e-mail. Responde 202 SEMPRE — anti-enumeracao.
 * `reset`   -> confere o codigo, troca a senha e derruba todas as sessoes.
 *
 * Camadas contra abuso (ver PasswordResetProperties):
 *  - forgot: 1 e-mail por endereco a cada `sendCooldown`; RateLimitFilter
 *    limita a rota por IP (4 / 15 min).
 *  - reset:  bloqueio POR IP apos `maxAttempts` codigos errados; e cada token
 *    morre depois de `maxAttempts` erros, nao importa o IP.
 */
@Service
class PasswordResetService(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val guard: PasswordResetAttemptGuard,
    private val mail: PasswordResetMailer,
    private val props: PasswordResetProperties,
    private val tx: PasswordResetTx,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /** email -> epoch millis do ultimo envio (anti-flood de caixa de entrada). */
    private val lastSent = ConcurrentHashMap<String, Long>()

    fun forgot(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        val user = users.findByEmail(email)
        if (user == null || (user.passwordHash == null && user.googleSub != null)) {
            log.info("forgot-password ignorado (sem conta ou conta so-Google)")
            return
        }

        // Cooldown de envio pro mesmo endereco. Corrida aqui, no maximo, manda
        // 2 e-mails — aceitavel; o objetivo e frear flood, nao ser exato.
        val now = System.currentTimeMillis()
        val prev = lastSent[email]
        if (prev != null && now - prev < props.sendCooldown.toMillis()) {
            log.info("forgot-password para @{} dentro do cooldown de envio", user.nickname)
            return
        }
        lastSent[email] = now

        val code = "%06d".format(random.nextInt(1_000_000))
        tx.createToken(user.id, sha256(code), Instant.now().plus(props.codeTtl))
        // Envio fora de qualquer transacao/lock: a chamada HTTP e lenta e o
        // ResendMailSender ja engole os erros (o endpoint responde 202 de todo jeito).
        mail.sendPasswordResetCode(user.email, code, props.codeTtl.toMinutes())
        log.info("Codigo de recuperacao gerado para @{}", user.nickname)
    }

    fun reset(rawEmail: String, code: String, newPassword: String, ip: String) {
        guard.assertNotLocked(ip)

        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            throw ValidationException(
                "weak_password",
                "A senha precisa de pelo menos $MIN_PASSWORD_LENGTH caracteres",
                "password",
            )
        }

        val email = rawEmail.trim().lowercase()
        val user = users.findByEmail(email)
        val token = user
            ?.let { tokens.findAllByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(it.id).firstOrNull() }
            ?.takeIf { it.isUsable(Instant.now()) }

        if (user == null || token == null) {
            guard.recordFailure(ip)   // lanca
        }

        // Registra a tentativa e (se estourou o teto) queima o token — em
        // transacao propria, para o `recordFailure` abaixo nao dar rollback nisso.
        val stillValid = tx.recordAttempt(token.id, props.maxAttempts)

        if (!stillValid || sha256(code.trim()) != token.codeHash) {
            guard.recordFailure(ip)   // lanca
        }

        tx.applyNewPassword(user.id, token.id, newPassword)
        guard.recordSuccess(ip)
        log.info("Senha redefinida para @{}", user.nickname)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/**
 * Escritas transacionais do reset, isoladas: a de "conta a tentativa" precisa
 * ser commitada mesmo quando o codigo esta errado e o servico devolve 401.
 */
@Component
class PasswordResetTx(
    private val tokens: PasswordResetTokenRepository,
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /** Invalida codigos antigos do usuario e grava o novo. */
    @Transactional
    fun createToken(userId: UUID, codeHash: String, expiresAt: Instant) {
        tokens.consumeAllForUser(userId, Instant.now())
        tokens.save(PasswordResetToken(userId = userId, codeHash = codeHash, expiresAt = expiresAt))
    }

    /** Incrementa attempts; se bateu o teto, consome o token. Devolve se ainda vale. */
    @Transactional
    fun recordAttempt(tokenId: UUID, maxAttempts: Int): Boolean {
        val t = tokens.findById(tokenId).orElse(null) ?: return false
        t.attempts = (t.attempts + 1).toShort()
        val exhausted = t.attempts >= maxAttempts
        if (exhausted) t.consumedAt = Instant.now()
        tokens.save(t)
        return !exhausted && t.consumedAt == null
    }

    @Transactional
    fun applyNewPassword(userId: UUID, tokenId: UUID, newPassword: String) {
        tokens.findById(tokenId).ifPresent { it.consumedAt = Instant.now(); tokens.save(it) }
        val user = users.findById(userId).orElseThrow()
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        user.updatedAt = Instant.now()
        users.save(user)
        // Senha nova: derruba todas as sessoes (inclusive a de quem tinha a senha antiga).
        refreshTokens.revokeAllForUser(user.id, Instant.now())
    }
}
