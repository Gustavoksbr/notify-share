package com.notifyshare.shared.web

import java.util.UUID

/**
 * Identidade autenticada, disponivel nos controllers via @AuthenticationPrincipal.
 *
 * Fica em shared/web porque toda feature autenticada depende dela, nao so a auth.
 */
data class AuthenticatedUser(val id: UUID, val nickname: String)
