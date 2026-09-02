package com.notifyshare.grants.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.friends.application.FriendService
import com.notifyshare.grants.adapter.persistence.GrantAuditRepository
import com.notifyshare.grants.adapter.persistence.GrantRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleSenderRepository
import com.notifyshare.grants.domain.Grant
import com.notifyshare.grants.domain.GrantAudit
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushNotifier
import com.notifyshare.shared.web.ConflictException
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class GrantView(
    val id: UUID,
    /** nickname da outra ponta */
    val counterpart: String,
    /** meu papel neste grant: sharer | recipient */
    val role: String,
    val status: String,
    val enabledApps: Int,
    val hasSpecificSenders: Boolean,
    val initiatedByMe: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingGrants(
    val incoming: List<GrantView>,
    val outgoing: List<GrantView>,
)

@Service
class GrantService(
    private val grants: GrantRepository,
    private val rules: GrantRuleRepository,
    private val ruleSenders: GrantRuleSenderRepository,
    private val audits: GrantAuditRepository,
    private val users: UserRepository,
    private val friends: FriendService,
    private val pushNotifier: PushNotifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // --- criacao: oferecer ou pedir ------------------------------------------

    @Transactional
    fun offer(sharerId: UUID, recipientNickname: String): GrantView {
        val recipient = resolve(recipientNickname)
        guard(sharerId, recipient.id)

        val existing = grants.findBySharerIdAndRecipientId(sharerId, recipient.id)
        val grant = when (existing?.status) {
            null -> Grant(sharerId = sharerId, recipientId = recipient.id, status = Grant.OFFERED, initiatedBy = sharerId)
            Grant.REVOKED -> existing.also { it.status = Grant.OFFERED }
            Grant.REQUESTED -> existing.also { it.status = Grant.ACTIVE } // ele ja tinha pedido
            else -> throw ConflictException("grant_exists", "Ja existe um compartilhamento com @${recipient.nickname}")
        }
        return persistAndNotify(grant, sharerId, recipient.id)
    }

    @Transactional
    fun request(recipientId: UUID, sharerNickname: String): GrantView {
        val sharer = resolve(sharerNickname)
        guard(sharer.id, recipientId)

        val existing = grants.findBySharerIdAndRecipientId(sharer.id, recipientId)
        val grant = when (existing?.status) {
            null -> Grant(sharerId = sharer.id, recipientId = recipientId, status = Grant.REQUESTED, initiatedBy = recipientId)
            Grant.REVOKED -> existing.also { it.status = Grant.REQUESTED }
            Grant.OFFERED -> existing.also { it.status = Grant.ACTIVE } // ela ja tinha oferecido
            else -> throw ConflictException("grant_exists", "Ja existe um compartilhamento com @${sharer.nickname}")
        }
        return persistAndNotify(grant, recipientId, sharer.id)
    }

    // --- transicoes de estado ---------------------------------------------

    @Transactional
    fun accept(userId: UUID, grantId: UUID): GrantView {
        val g = load(grantId)
        val waitingForMe = (g.status == Grant.OFFERED && userId == g.recipientId) ||
            (g.status == Grant.REQUESTED && userId == g.sharerId)
        if (!waitingForMe) throw ForbiddenException("cannot_accept", "Esse pedido nao esta esperando por voce")
        g.status = Grant.ACTIVE
        return persistAndNotify(g, userId, g.counterpart(userId), action = "activated")
    }

    /**
     * Recusa (pelo lado que espera) ou cancela (por quem iniciou) um pedido
     * pendente. Nos dois casos a linha some — nao guardamos "recusado".
     */
    @Transactional
    fun decline(userId: UUID, grantId: UUID) {
        val g = load(grantId)
        if (!g.isPending) throw ConflictException("not_pending", "Esse compartilhamento nao esta pendente")
        if (!g.involves(userId)) throw ForbiddenException("not_in_grant", "Voce nao faz parte deste pedido")
        clearRules(g.id)
        grants.delete(g)
        pushGrant(g, to = g.counterpart(userId), action = "cancelled", by = userId)
    }

    @Transactional
    fun pause(userId: UUID, grantId: UUID): GrantView {
        val g = load(grantId)
        if (!g.involves(userId)) throw ForbiddenException("not_in_grant", "Voce nao faz parte deste compartilhamento")
        if (g.status != Grant.ACTIVE) throw ConflictException("not_active", "So da para pausar um compartilhamento ativo")
        g.status = if (userId == g.sharerId) Grant.PAUSED_BY_SHARER else Grant.PAUSED_BY_RECIPIENT
        return persistAndNotify(g, userId, g.counterpart(userId), action = "paused")
    }

    @Transactional
    fun resume(userId: UUID, grantId: UUID): GrantView {
        val g = load(grantId)
        val canResume = (g.status == Grant.PAUSED_BY_SHARER && userId == g.sharerId) ||
            (g.status == Grant.PAUSED_BY_RECIPIENT && userId == g.recipientId)
        if (!canResume) throw ForbiddenException("cannot_resume", "So quem pausou pode retomar")
        g.status = Grant.ACTIVE
        return persistAndNotify(g, userId, g.counterpart(userId), action = "resumed")
    }

    @Transactional
    fun revoke(userId: UUID, grantId: UUID) {
        val g = load(grantId)
        if (!g.involves(userId)) throw ForbiddenException("not_in_grant", "Voce nao faz parte deste compartilhamento")
        clearRules(g.id)
        g.status = Grant.REVOKED
        g.updatedAt = Instant.now()
        grants.save(g)
        audits.save(GrantAudit(grantId = g.id, actorId = userId, action = GrantAudit.REVOKED))
        pushGrant(g, to = g.counterpart(userId), action = "revoked", by = userId)
    }

    /** Chamado pelo GrantRuleService quando as regras mudam. */
    @Transactional
    fun recordRulesChanged(grantId: UUID, actorId: UUID, detail: String?) {
        audits.save(GrantAudit(grantId = grantId, actorId = actorId, action = GrantAudit.RULES_CHANGED, detail = detail))
    }

    @Transactional(readOnly = true)
    fun timelineBetween(a: UUID, b: UUID): List<GrantAudit> {
        val grantIds = grants.findAllBetween(a, b).map { it.id }
        return if (grantIds.isEmpty()) emptyList()
        else audits.findAllByGrantIdInOrderByCreatedAtDesc(grantIds)
    }

    // --- consultas -------------------------------------------------------

    @Transactional(readOnly = true)
    fun listForSharer(userId: UUID): List<GrantView> =
        viewAll(grants.findAllBySharerIdAndStatusIn(userId, Grant.LIVE), userId).sortedBy { it.counterpart }

    @Transactional(readOnly = true)
    fun listForRecipient(userId: UUID): List<GrantView> =
        viewAll(grants.findAllByRecipientIdAndStatusIn(userId, Grant.LIVE), userId).sortedBy { it.counterpart }

    @Transactional(readOnly = true)
    fun pending(userId: UUID): PendingGrants {
        val (incoming, outgoing) = grants.findPendingFor(userId).partition { g ->
            (g.status == Grant.OFFERED && g.recipientId == userId) ||
                (g.status == Grant.REQUESTED && g.sharerId == userId)
        }
        return PendingGrants(viewAll(incoming, userId), viewAll(outgoing, userId))
    }

    /** Grants ativos em que este usuario e a origem — usado pelo roteamento de eventos. */
    @Transactional(readOnly = true)
    fun activeOutbound(sharerId: UUID): List<Grant> =
        grants.findAllBySharerIdAndStatusIn(sharerId, setOf(Grant.ACTIVE))

    // --- internos -------------------------------------------------------

    private fun guard(sharerId: UUID, recipientId: UUID) {
        if (sharerId == recipientId) throw ValidationException("self_grant", "Voce nao compartilha com voce mesmo")
        if (!friends.areFriends(sharerId, recipientId)) {
            throw ForbiddenException("not_friends", "Voces precisam ser amigos antes de compartilhar")
        }
    }

    private fun resolve(nickname: String) =
        users.findByNickname(nickname.trim().lowercase().removePrefix("@"))
            ?: throw NotFoundException("user_not_found", "Nao existe ninguem com esse nickname")

    private fun load(grantId: UUID): Grant =
        grants.findById(grantId).orElseThrow { NotFoundException("grant_not_found", "Compartilhamento nao encontrado") }

    private fun persistAndNotify(
        grant: Grant,
        actorId: UUID,
        counterpartId: UUID,
        action: String? = null,
    ): GrantView {
        grant.updatedAt = Instant.now()
        grants.save(grant)
        val resolved = action ?: when (grant.status) {
            Grant.OFFERED -> "offered"
            Grant.REQUESTED -> "requested"
            Grant.ACTIVE -> "activated"
            else -> grant.status
        }
        AUDIT_ACTIONS[resolved]?.let {
            audits.save(GrantAudit(grantId = grant.id, actorId = actorId, action = it))
        }
        pushGrant(grant, to = counterpartId, action = resolved, by = actorId)
        return view(grant, actorId)
    }

    private fun pushGrant(grant: Grant, to: UUID, action: String, by: UUID) {
        val actor = users.findById(by).map { it.nickname }.orElse("")
        pushNotifier.notifyUser(
            to,
            PushMessage(
                type = "grant",
                // "from" e chave reservada do FCM em payload de dados
                data = mapOf("grantId" to grant.id.toString(), "action" to action, "actor" to actor),
            ),
        )
        log.debug("grant {} -> {} ({})", grant.id, action, to)
    }

    private fun clearRules(grantId: UUID) {
        val ids = rules.findAllByGrantId(grantId).map { it.id }
        if (ids.isNotEmpty()) ruleSenders.deleteAllByRuleIdIn(ids)
        rules.deleteAllByGrantId(grantId)
    }

    private fun view(g: Grant, viewerId: UUID): GrantView = viewAll(listOf(g), viewerId).first()

    private fun viewAll(list: List<Grant>, viewerId: UUID): List<GrantView> {
        if (list.isEmpty()) return emptyList()
        val names = users.findAllByIdIn(list.map { it.counterpart(viewerId) }.toSet())
            .associate { it.id to it.nickname }
        val rulesByGrant = rules.findAllByGrantIdIn(list.map { it.id }).groupBy { it.grantId }
        return list.map { g ->
            val enabled = rulesByGrant[g.id].orEmpty().filter { it.enabled }
            GrantView(
                id = g.id,
                counterpart = names[g.counterpart(viewerId)] ?: "?",
                role = if (viewerId == g.sharerId) "sharer" else "recipient",
                status = g.status,
                enabledApps = enabled.size,
                hasSpecificSenders = enabled.any { !it.allSenders },
                initiatedByMe = g.initiatedBy == viewerId,
                createdAt = g.createdAt,
                updatedAt = g.updatedAt,
            )
        }
    }

    private companion object {
        val AUDIT_ACTIONS = mapOf(
            "offered" to GrantAudit.OFFERED,
            "requested" to GrantAudit.REQUESTED,
            "activated" to GrantAudit.ACTIVATED,
            "paused" to GrantAudit.PAUSED,
            "resumed" to GrantAudit.RESUMED,
        )
    }
}
