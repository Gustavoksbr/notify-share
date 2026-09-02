package com.notifyshare.blocks.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.blocks.adapter.persistence.BlockRepository
import com.notifyshare.blocks.domain.Block
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class BlockedUserView(val nickname: String, val since: Instant)

@Service
class BlockService(
    private val blocks: BlockRepository,
    private val users: UserRepository,
) {

    // --- consultas usadas por outras features ------------------------------

    /** Eu bloqueei essa pessoa. */
    @Transactional(readOnly = true)
    fun iBlocked(meId: UUID, otherId: UUID): Boolean =
        blocks.existsByBlockerIdAndBlockedId(meId, otherId)

    /** Essa pessoa me bloqueou. */
    @Transactional(readOnly = true)
    fun blockedMe(meId: UUID, otherId: UUID): Boolean =
        blocks.existsByBlockerIdAndBlockedId(otherId, meId)

    /** Ha bloqueio em qualquer sentido. */
    @Transactional(readOnly = true)
    fun eitherBlocked(a: UUID, b: UUID): Boolean = blocks.existsEitherDirection(a, b)

    // --- mutacoes ---------------------------------------------------------

    @Transactional
    fun block(meId: UUID, targetNickname: String): BlockedUserView {
        val target = resolve(targetNickname)
        if (target.id == meId) throw ValidationException("self_block", "Voce nao pode bloquear voce mesmo")
        val existing = blocks.findByBlockerIdAndBlockedId(meId, target.id)
        val block = existing ?: blocks.save(Block(blockerId = meId, blockedId = target.id))
        return BlockedUserView(target.nickname, block.createdAt)
    }

    @Transactional
    fun unblock(meId: UUID, targetNickname: String) {
        val target = users.findByNickname(normalize(targetNickname)) ?: return
        blocks.findByBlockerIdAndBlockedId(meId, target.id)?.let { blocks.delete(it) }
    }

    @Transactional(readOnly = true)
    fun listBlocked(meId: UUID): List<BlockedUserView> {
        val rows = blocks.findAllByBlockerId(meId)
        val names = users.findAllByIdIn(rows.map { it.blockedId }).associate { it.id to it.nickname }
        return rows.mapNotNull { b -> names[b.blockedId]?.let { BlockedUserView(it, b.createdAt) } }
            .sortedBy { it.nickname }
    }

    // --- internos -------------------------------------------------------

    private fun resolve(nickname: String) =
        users.findByNickname(normalize(nickname))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

    private fun normalize(nickname: String) = nickname.trim().lowercase().removePrefix("@")
}
