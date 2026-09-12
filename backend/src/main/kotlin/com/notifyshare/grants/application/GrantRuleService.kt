package com.notifyshare.grants.application

import com.notifyshare.grants.adapter.persistence.GrantRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleSenderRepository
import com.notifyshare.grants.domain.Grant
import com.notifyshare.grants.domain.GrantRule
import com.notifyshare.grants.domain.GrantRuleSender
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushNotifier
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
    val textFilters: List<String> = emptyList(),
    val allowCodes: Boolean = false,
)

data class SenderInput(val senderHash: String, val senderLabel: String? = null)

data class RuleInput(
    val packageName: String,
    val enabled: Boolean = true,
    val contentMode: String = GrantRule.CONTENT,
    val allSenders: Boolean = true,
    val batteryThreshold: Int? = null,
    val senders: List<SenderInput> = emptyList(),
    val textFilters: List<String> = emptyList(),
    val allowCodes: Boolean = false,
)

@Service
class GrantRuleService(
    private val grants: GrantRepository,
    private val rules: GrantRuleRepository,
    private val ruleSenders: GrantRuleSenderRepository,
    private val pushNotifier: PushNotifier,
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
                    textFilters = input.textFilters
                        .map { it.trim() }.filter { it.isNotEmpty() }
                        .distinct().take(30).joinToString("\n").ifBlank { null },
                    allowCodes = input.allowCodes,
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
        // Sem mais audit de "regras atualizadas" na conversa — com o autosave do
        // app (uma edicao qualquer salva sozinha, debounced), isso viraria uma
        // mensagem a cada toque. Os dois lados ainda releem a contagem de apps
        // etc. na hora, so que via push, nao via linha na conversa.
        val frame = PushMessage(
            type = "grant",
            data = mapOf("grantId" to grantId.toString(), "action" to "rules_changed"),
        )
        pushNotifier.notifyUser(grant.sharerId, frame)
        pushNotifier.notifyUser(grant.recipientId, frame)
        return toViews(rules.findAllByGrantId(grantId))
    }

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
                    textFilters = r.textFilters?.split("\n")?.filter { it.isNotBlank() }.orEmpty(),
                    allowCodes = r.allowCodes,
                )
            }
            .sortedBy { it.packageName }
    }

    private companion object {
        val MODES = setOf(GrantRule.CONTENT, GrantRule.SENDER_ONLY, GrantRule.PAUSED)
    }
}
