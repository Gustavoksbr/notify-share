package com.notifyshare.grants.application

import com.notifyshare.grants.adapter.persistence.GrantAuditRepository
import com.notifyshare.grants.adapter.persistence.GrantRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleSenderRepository
import com.notifyshare.grants.domain.Grant
import com.notifyshare.grants.domain.GrantAudit
import com.notifyshare.grants.domain.GrantRule
import com.notifyshare.grants.domain.GrantRuleSender
import com.notifyshare.shared.web.ForbiddenException
import com.notifyshare.shared.web.NotFoundException
import com.notifyshare.shared.web.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class SenderView(val senderHash: String, val senderLabel: String?)

data class RuleView(
    val packageName: String,
    val enabled: Boolean,
    val contentMode: String,
    val allSenders: Boolean,
    val batteryThreshold: Int?,
    val senders: List<SenderView>,
)

data class SenderInput(val senderHash: String, val senderLabel: String? = null)

data class RuleInput(
    val packageName: String,
    val enabled: Boolean = true,
    val contentMode: String = GrantRule.CONTENT,
    val allSenders: Boolean = true,
    val batteryThreshold: Int? = null,
    val senders: List<SenderInput> = emptyList(),
)

@Service
class GrantRuleService(
    private val grants: GrantRepository,
    private val rules: GrantRuleRepository,
    private val ruleSenders: GrantRuleSenderRepository,
    private val audits: GrantAuditRepository,
) {

    @Transactional(readOnly = true)
    fun get(userId: UUID, grantId: UUID): List<RuleView> {
        val grant = load(grantId)
        if (!grant.involves(userId)) throw ForbiddenException("not_in_grant", "Voce nao faz parte deste compartilhamento")
        return toViews(rules.findAllByGrantId(grantId))
    }

    /** Substitui a lista inteira. Regras sao poucas; comparar diffs nao paga. */
    @Transactional
    fun replace(userId: UUID, grantId: UUID, inputs: List<RuleInput>): List<RuleView> {
        val grant = load(grantId)
        if (grant.sharerId != userId) {
            throw ForbiddenException("not_sharer", "So quem compartilha edita as regras")
        }

        val previousIds = rules.findAllByGrantId(grantId).map { it.id }
        if (previousIds.isNotEmpty()) ruleSenders.deleteAllByRuleIdIn(previousIds)
        rules.deleteAllByGrantId(grantId)
        rules.flush() // libera o unique (grant_id, package_name) antes dos inserts

        inputs.distinctBy { it.packageName.trim() }.forEach { input ->
            if (input.contentMode !in MODES) {
                throw ValidationException("invalid_content_mode", "contentMode deve ser content, sender_only ou paused", "contentMode")
            }
            val rule = rules.save(
                GrantRule(
                    grantId = grantId,
                    packageName = input.packageName.trim(),
                    enabled = input.enabled,
                    contentMode = input.contentMode,
                    allSenders = input.allSenders,
                    batteryThreshold = input.batteryThreshold?.coerceIn(1, 99),
                )
            )
            if (!input.allSenders) {
                input.senders.distinctBy { it.senderHash.trim() }.forEach { s ->
                    ruleSenders.save(
                        GrantRuleSender(
                            ruleId = rule.id,
                            senderHash = s.senderHash.trim(),
                            senderLabel = s.senderLabel?.take(120),
                        )
                    )
                }
            }
        }
        audits.save(
            GrantAudit(
                grantId = grantId,
                actorId = userId,
                action = GrantAudit.RULES_CHANGED,
                detail = auditDetail(inputs),
            )
        )
        return toViews(rules.findAllByGrantId(grantId))
    }

    /** "WhatsApp, Bateria" — os apps ligados, para a linha na conversa. */
    private fun auditDetail(inputs: List<RuleInput>): String? =
        inputs.filter { it.enabled && it.contentMode != GrantRule.PAUSED }
            .joinToString(", ") { it.packageName.substringAfterLast('.').substringAfter(':') }
            .take(200)
            .ifBlank { null }

    private fun load(grantId: UUID): Grant =
        grants.findById(grantId).orElseThrow { NotFoundException("grant_not_found", "Compartilhamento nao encontrado") }

    private fun toViews(ruleList: List<GrantRule>): List<RuleView> {
        val sendersByRule = ruleSenders.findAllByRuleIdIn(ruleList.map { it.id }).groupBy { it.ruleId }
        return ruleList
            .map { r ->
                RuleView(
                    packageName = r.packageName,
                    enabled = r.enabled,
                    contentMode = r.contentMode,
                    allSenders = r.allSenders,
                    batteryThreshold = r.batteryThreshold,
                    senders = sendersByRule[r.id].orEmpty().map { SenderView(it.senderHash, it.senderLabel) },
                )
            }
            .sortedBy { it.packageName }
    }

    private companion object {
        val MODES = setOf(GrantRule.CONTENT, GrantRule.SENDER_ONLY, GrantRule.PAUSED)
    }
}
