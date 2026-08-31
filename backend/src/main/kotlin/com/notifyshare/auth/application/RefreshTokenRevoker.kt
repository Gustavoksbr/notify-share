package com.notifyshare.auth.application

import com.notifyshare.auth.adapter.persistence.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Revogacao em transacao propria.
 *
 * Existe como bean separado por um motivo especifico: quando detectamos reuso
 * de refresh token, precisamos revogar a familia E devolver 401. Se as duas
 * coisas acontecessem na mesma transacao, a excecao causaria rollback e a
 * revogacao seria desfeita — o atacante levaria um 401 e continuaria com um
 * token valido. REQUIRES_NEW faz a revogacao ser commitada antes do throw.
 *
 * Precisa ser outro bean porque chamada interna nao passa pelo proxy do Spring
 * e a anotacao seria ignorada.
 */
@Component
class RefreshTokenRevoker(private val refreshTokens: RefreshTokenRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID) {
        val count = refreshTokens.revokeFamily(familyId, Instant.now())
        log.warn("Familia de refresh token {} revogada, {} tokens atingidos", familyId, count)
    }
}
