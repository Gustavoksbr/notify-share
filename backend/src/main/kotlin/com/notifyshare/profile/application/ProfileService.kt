package com.notifyshare.profile.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.blocks.application.BlockService
import com.notifyshare.friends.application.FriendService
import com.notifyshare.grants.application.GrantService
import com.notifyshare.grants.application.GrantView
import com.notifyshare.shared.web.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Visao publica do perfil de outra pessoa, agregando o que ja existe: amizade,
 * bloqueio e os grants entre nos dois. Feature separada porque cruza tres
 * outras (evita dependencia circular com GrantService).
 */
data class UserProfileView(
    val nickname: String,
    val friend: Boolean,
    val blockedByMe: Boolean,
    /** Grants em que EU compartilho com essa pessoa. */
    val sharingWithThem: List<GrantView>,
    /** Grants em que essa pessoa compartilha comigo. */
    val receivingFromThem: List<GrantView>,
    /** Pedidos/ofertas pendentes que EU iniciei com essa pessoa (da para cancelar). */
    val outgoingPending: List<GrantView>,
)

@Service
class ProfileService(
    private val users: UserRepository,
    private val friends: FriendService,
    private val grants: GrantService,
    private val blocks: BlockService,
) {

    @Transactional(readOnly = true)
    fun profileOf(meId: UUID, rawNickname: String): UserProfileView {
        val nickname = rawNickname.trim().lowercase().removePrefix("@")
        val target = users.findByNickname(nickname)
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

        val sharing = grants.listForSharer(meId).filter { it.counterpart.equals(target.nickname, true) }
        val receiving = grants.listForRecipient(meId).filter { it.counterpart.equals(target.nickname, true) }
        val outgoing = grants.pending(meId).outgoing.filter { it.counterpart.equals(target.nickname, true) }

        return UserProfileView(
            nickname = target.nickname,
            friend = friends.areFriends(meId, target.id),
            blockedByMe = blocks.iBlocked(meId, target.id),
            sharingWithThem = sharing,
            receivingFromThem = receiving,
            outgoingPending = outgoing,
        )
    }
}
