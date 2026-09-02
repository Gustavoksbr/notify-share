package com.notifyshare.friends.application

import java.util.UUID

/**
 * Publicado quando um pedido de amizade e aceito (pelo destinatario, ou na hora
 * quando os dois lados se pediram). Se o solicitante marcou que ja queria
 * compartilhar/receber, o modulo de grants escuta isto e cria os grants
 * pendentes — na direcao certa, iniciados pelo solicitante.
 *
 * O consumo e AFTER_COMMIT: a amizade vale de qualquer forma, a criacao dos
 * grants e conveniencia e nao pode desfazer a aceitacao se falhar.
 */
data class FriendshipAcceptedEvent(
    /** Quem pediu a amizade (e marcou as intencoes de compartilhamento). */
    val requesterId: UUID,
    /** Nickname de quem foi pedido — o "outro lado" do grant. */
    val addresseeNickname: String,
    val alsoOfferShare: Boolean,
    val alsoRequestShare: Boolean,
) {
    val wantsAnything: Boolean get() = alsoOfferShare || alsoRequestShare
}
