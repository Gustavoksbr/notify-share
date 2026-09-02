package com.notifyshare.realtime.application

import java.util.UUID

/**
 * Publicado pelo gateway quando um usuario abre a primeira sessao WebSocket ou
 * fecha a ultima. Um listener avisa os amigos que estao online agora.
 *
 * "Online" = tem WebSocket vivo = app aberto e em primeiro plano.
 */
data class PresenceChanged(val userId: UUID, val online: Boolean)
