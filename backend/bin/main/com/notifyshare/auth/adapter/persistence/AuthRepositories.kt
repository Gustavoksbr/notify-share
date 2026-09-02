package com.notifyshare.auth.adapter.persistence

import com.notifyshare.auth.domain.RefreshToken
import com.notifyshare.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * JpaRepository ja e a porta. Nao existe um adapter em volta dela: envolver
 * isso numa interface propria so acrescentaria arquivos sem desacoplar nada,
 * ja que a troca de Postgres por outro banco nao esta no horizonte.
 */
interface UserRepository : JpaRepository<User, UUID> {

    fun findByNickname(nickname: String): User?

    fun findByEmail(email: String): User?

    fun existsByNickname(nickname: String): Boolean

    fun existsByEmail(email: String): Boolean

    /** Busca de pessoas por nickname. O nickname ja e gravado em minusculas. */
    fun findTop20ByNicknameStartingWithAndIdNot(prefix: String, id: UUID): List<User>

    fun findAllByIdIn(ids: Collection<UUID>): List<User>

    fun findAllByNicknameIn(nicknames: Collection<String>): List<User>
}

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** Usado na deteccao de reuso: derruba a cadeia inteira de uma vez. */
    @Modifying
    @Query(
        """
        update RefreshToken t
           set t.revokedAt = :now
         where t.familyId = :familyId
           and t.revokedAt is null
        """
    )
    fun revokeFamily(@Param("familyId") familyId: UUID, @Param("now") now: Instant): Int

    @Modifying
    @Query(
        """
        update RefreshToken t
           set t.revokedAt = :now
         where t.userId = :userId
           and t.revokedAt is null
        """
    )
    fun revokeAllForUser(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}
