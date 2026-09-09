package com.notifyshare.grants.application

import com.notifyshare.grants.adapter.persistence.GrantRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleSenderRepository
import com.notifyshare.grants.adapter.persistence.RecipientAppMuteRepository
import com.notifyshare.grants.domain.RecipientAppMute
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Regras do lado de quem RECEBE. Hoje so uma: silenciar um app (o evento
 * continua entrando no feed, so nao dispara push). Nao mexe no que o sharer
 * compartilha — isso e [GrantRuleService].
 */
data class RecipientAppRuleView(
    val packageName: String,
    /** o que o sharer libera nesse app: content | sender_only | paused */
    val sharerMode: String,
    val enabledBySharer: Boolean,
    /** false = eu silenciei esse app; ele ainda aparece no feed */
    val notify: Boolean,
    /** true = o sharer libera TODOS os remetentes desse app. */
    val allSenders: Boolean = true,
    /**
     * Quando allSenders=false: os nomes (rotulos) dos remetentes que o sharer
     * escolheu liberar, para o destinatario saber de quem ele recebe. Vazio
     * quando allSenders=true ou quando nenhum remetente tem rotulo.
     */
    val senders: List<String> = emptyList(),
)

@Service
class RecipientRuleService(
    private val grants: GrantRepository,
    private val rules: GrantRuleRepository,
    private val ruleSenders: GrantRuleSenderRepository,
    private val mutes: RecipientAppMuteRepository,
) {

    @Transactional(readOnly = true)
    fun list(recipientId: UUID, grantId: UUID): List<RecipientAppRuleView> {
        requireRecipient(recipientId, grantId)
        val muted = mutes.findAllByGrantId(grantId).map { it.packageName }.toSet()
        val ruleList = rules.findAllByGrantId(grantId).sortedBy { it.packageName }
        // Rotulos dos remetentes especificos, so das regras que restringem.
        val restricting = ruleList.filterNot { it.allSenders }.map { it.id }
        val labelsByRule = if (restricting.isEmpty()) {
            emptyMap()
        } else {
            ruleSenders.findAllByRuleIdIn(restricting)
                .groupBy({ it.ruleId }, { it.senderLabel })
                .mapValues { (_, labels) -> labels.filterNotNull().distinct() }
        }
        return ruleList.map {
            RecipientAppRuleView(
                packageName = it.packageName,
                sharerMode = it.contentMode,
                enabledBySharer = it.enabled,
                notify = it.packageName !in muted,
                allSenders = it.allSenders,
                senders = if (it.allSenders) emptyList() else labelsByRule[it.id].orEmpty(),
            )
        }
    }

    @Transactional
    fun setNotify(recipientId: UUID, grantId: UUID, packageName: String, notify: Boolean) {
        requireRecipient(recipientId, grantId)
        val existing = mutes.findByGrantIdAndPackageName(grantId, packageName)
        when {
            notify && existing != null -> mutes.delete(existing)
            !notify && existing == null ->
                mutes.save(RecipientAppMute(grantId = grantId, recipientId = recipientId, packageName = packageName))
        }
    }

    private fun requireRecipient(recipientId: UUID, grantId: UUID) {
        val grant = grants.findById(grantId)
            .orElseThrow { NotFoundException("grant_not_found", "Compartilhamento nao encontrado") }
        if (grant.recipientId != recipientId) {
            throw ForbiddenException("not_recipient", "Voce nao recebe deste compartilhamento")
        }
    }
}
