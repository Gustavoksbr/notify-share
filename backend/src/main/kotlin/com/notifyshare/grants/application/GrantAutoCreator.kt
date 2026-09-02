package com.notifyshare.grants.application

import com.notifyshare.friends.application.FriendshipAcceptedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Quando um pedido de amizade e aceito e o solicitante ja tinha marcado que
 * queria compartilhar/receber, cria os grants pendentes correspondentes.
 *
 * Fica num bean separado de propósito: o listener precisa chamar
 * [GrantService.offer]/[GrantService.request] pelo proxy (senao o `@Transactional`
 * deles nao vale), e roda AFTER_COMMIT para nao desfazer a amizade se algo aqui
 * falhar. Um grant que ja existe apenas loga e segue.
 */
@Component
class GrantAutoCreator(private val grants: GrantService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onFriendshipAccepted(event: FriendshipAcceptedEvent) {
        if (event.alsoOfferShare) {
            runCatching { grants.offer(event.requesterId, event.addresseeNickname) }
                .onFailure { log.info("auto-offer para @{} nao criado: {}", event.addresseeNickname, it.message) }
        }
        if (event.alsoRequestShare) {
            runCatching { grants.request(event.requesterId, event.addresseeNickname) }
                .onFailure { log.info("auto-request de @{} nao criado: {}", event.addresseeNickname, it.message) }
        }
    }
}
