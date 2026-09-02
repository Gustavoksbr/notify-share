package com.notifyshare.friends.application

import java.util.UUID

/**
 * Publicado quando o vinculo de amizade entre dois usuarios deixa de existir:
 * um pedido de amizade cancelado pelo solicitante, ou um "desfazer amizade".
 * O modulo de grants escuta e apaga qualquer compartilhamento entre os dois —
 * inclusive os pedidos que tinham vindo junto do pedido de amizade.
 */
data class FriendLinkClearedEvent(val userAId: UUID, val userBId: UUID)
