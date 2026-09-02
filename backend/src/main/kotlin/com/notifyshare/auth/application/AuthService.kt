package com.notifyshare.auth.application

import com.notifyshare.auth.adapter.google.GoogleTokenVerifier
import com.notifyshare.auth.adapter.persistence.RefreshTokenRepository
import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.auth.adapter.token.JwtService
import com.notifyshare.auth.domain.Nickname
import com.notifyshare.auth.domain.RefreshToken
import com.notifyshare.auth.domain.User
import com.notifyshare.shared.web.ConflictException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.UnauthorizedException
import com.notifyshare.shared.web.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: User,
)

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val jwt: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val revoker: RefreshTokenRevoker,
    private val googleVerifier: GoogleTokenVerifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(rawNickname: String, rawEmail: String, password: String, deviceLabel: String?): IssuedTokens {
        val nickname = Nickname.normalize(rawNickname)
        Nickname.validate(nickname)?.let { throw ValidationException("invalid_nickname", it, "nickname") }

        val email = rawEmail.trim().lowercase()
        if (!email.matches(EMAIL_SHAPE)) {
            throw ValidationException("invalid_email", "E-mail invalido", "email")
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw ValidationException(
                "weak_password",
                "A senha precisa de pelo menos $MIN_PASSWORD_LENGTH caracteres",
                "password",
            )
        }

        if (users.existsByNickname(nickname)) {
            throw ConflictException("nickname_taken", "Esse nickname ja esta em uso", "nickname")
        }
        // O e-mail nao e verificado (ADR-001). O UNIQUE existe para limitar
        // uma conta por endereco, nao para provar que o endereco e real.
        if (users.existsByEmail(email)) {
            throw ConflictException("email_taken", "Ja existe uma conta com esse e-mail", "email")
        }

        val user = users.save(
            User(
                nickname = nickname,
                email = email,
                passwordHash = passwordEncoder.encode(password)!!,
            )
        )
        log.info("Conta criada: @{}", nickname)
        return issueFor(user, familyId = UUID.randomUUID(), deviceLabel = deviceLabel)
    }

    @Transactional
    fun login(identifier: String, password: String, deviceLabel: String?): IssuedTokens {
        val normalized = identifier.trim().lowercase()
        val user = (if (normalized.contains('@')) users.findByEmail(normalized) else users.findByNickname(normalized))

        // Conta que so tem Google e o identificador foi um e-mail: vale a dica
        // (isso revela que o e-mail tem conta, mas e o que Slack/Figma tambem
        // fazem — o ganho de UX compensa). Por nickname, nao: seguiria oraculo.
        if (user != null && user.passwordHash == null && user.googleSub != null && normalized.contains('@')) {
            throw UnauthorizedException("use_google", "Essa conta entra com o Google")
        }

        // Mesmo erro para usuario inexistente, conta so-Google (por nickname) e
        // senha errada, de proposito: nao entregamos um oraculo de nicknames.
        val hash = user?.passwordHash
        if (user == null || hash == null || !passwordEncoder.matches(password, hash)) {
            throw UnauthorizedException("invalid_credentials", "Nickname ou senha incorretos")
        }

        return issueFor(user, familyId = UUID.randomUUID(), deviceLabel = deviceLabel)
    }

    /**
     * Login (ou cadastro) com uma conta Google.
     *
     * - ja vinculada pelo `sub`  -> entra
     * - existe conta com o mesmo e-mail -> vincula o Google a ela e entra
     * - conta nova sem `nickname` -> 409 needs_nickname (o app pergunta e repete)
     * - conta nova com `nickname` -> cadastra sem senha e entra
     */
    @Transactional
    fun loginWithGoogle(idToken: String, rawNickname: String?, deviceLabel: String?): IssuedTokens {
        val identity = googleVerifier.verify(idToken)
        if (!identity.emailVerified) {
            throw UnauthorizedException("google_email_unverified", "O e-mail dessa conta Google nao esta verificado")
        }
        val email = identity.email.trim().lowercase()

        users.findByGoogleSub(identity.sub)?.let {
            return issueFor(it, UUID.randomUUID(), deviceLabel)
        }

        users.findByEmail(email)?.let { existing ->
            existing.googleSub = identity.sub
            existing.updatedAt = Instant.now()
            users.save(existing)
            log.info("Conta @{} vinculada ao Google", existing.nickname)
            return issueFor(existing, UUID.randomUUID(), deviceLabel)
        }

        val nickname = rawNickname?.let(Nickname::normalize)
            ?: throw ConflictException("needs_nickname", email, "nickname")
        Nickname.validate(nickname)?.let { throw ValidationException("invalid_nickname", it, "nickname") }
        if (users.existsByNickname(nickname)) {
            throw ConflictException("nickname_taken", "Esse nickname ja esta em uso", "nickname")
        }

        val user = users.save(
            User(nickname = nickname, email = email, passwordHash = null, googleSub = identity.sub)
        )
        log.info("Conta criada via Google: @{}", nickname)
        return issueFor(user, UUID.randomUUID(), deviceLabel)
    }

    @Transactional
    fun refresh(refreshTokenValue: String, deviceLabel: String?): IssuedTokens {
        val hash = jwt.hashRefreshToken(refreshTokenValue)
        val stored = refreshTokens.findByTokenHash(hash)
            ?: throw UnauthorizedException("invalid_refresh_token", "Sessao invalida, entre novamente")

        // Deteccao de reuso: um token ja rotacionado ou revogado voltando
        // significa que alguem copiou o valor. Derruba a familia inteira.
        //
        // A revogacao vai por um bean com REQUIRES_NEW porque o throw logo abaixo
        // faria rollback desta transacao e desfaria a revogacao.
        if (!stored.isActive) {
            revoker.revokeFamily(stored.familyId)
            log.warn("Reuso de refresh token detectado, familia {} revogada", stored.familyId)
            throw UnauthorizedException("refresh_token_reused", "Sessao encerrada por seguranca, entre novamente")
        }

        val user = users.findById(stored.userId).orElseThrow {
            UnauthorizedException("invalid_refresh_token", "Sessao invalida, entre novamente")
        }

        val (issued, replacement) = issueWithEntity(
            user,
            familyId = stored.familyId,
            deviceLabel = deviceLabel ?: stored.deviceLabel,
        )
        stored.replacedBy = replacement.id
        stored.revokedAt = Instant.now()
        refreshTokens.save(stored)
        return issued
    }

    @Transactional
    fun logout(refreshTokenValue: String) {
        val stored = refreshTokens.findByTokenHash(jwt.hashRefreshToken(refreshTokenValue)) ?: return
        stored.revokedAt = Instant.now()
        refreshTokens.save(stored)
    }

    @Transactional
    fun logoutEverywhere(userId: UUID) {
        val count = refreshTokens.revokeAllForUser(userId, Instant.now())
        log.info("Encerradas {} sessoes do usuario {}", count, userId)
    }

    @Transactional(readOnly = true)
    fun findById(userId: UUID): User =
        users.findById(userId).orElseThrow { NotFoundException("user_not_found", "Usuario nao encontrado") }

    private fun issueFor(user: User, familyId: UUID, deviceLabel: String?): IssuedTokens =
        issueWithEntity(user, familyId, deviceLabel).first

    /**
     * Devolve tambem a entidade gravada porque a rotacao precisa do id dela
     * para apontar `replaced_by` sem uma consulta extra.
     */
    private fun issueWithEntity(
        user: User,
        familyId: UUID,
        deviceLabel: String?,
    ): Pair<IssuedTokens, RefreshToken> {
        val access = jwt.issueAccessToken(user.id, user.nickname)
        val refreshValue = jwt.generateRefreshTokenValue()

        val entity = refreshTokens.save(
            RefreshToken(
                userId = user.id,
                tokenHash = jwt.hashRefreshToken(refreshValue),
                familyId = familyId,
                deviceLabel = deviceLabel?.take(80),
                expiresAt = jwt.refreshExpiry(),
            )
        )

        val tokens = IssuedTokens(
            accessToken = access.value,
            refreshToken = refreshValue,
            expiresInSeconds = jwt.accessTtlSeconds,
            user = user,
        )
        return tokens to entity
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$")
    }
}
