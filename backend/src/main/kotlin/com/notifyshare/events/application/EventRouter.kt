package com.notifyshare.events.application

import com.notifyshare.auth.adapter.persistence.UserRepository
import com.notifyshare.events.adapter.persistence.EventDeliveryRepository
import com.notifyshare.events.domain.Event
import com.notifyshare.events.domain.EventDelivery
import com.notifyshare.grants.adapter.persistence.GrantRuleRepository
import com.notifyshare.grants.adapter.persistence.GrantRuleSenderRepository
import com.notifyshare.grants.adapter.persistence.RecipientAppMuteRepository
import com.notifyshare.grants.application.GrantService
import com.notifyshare.grants.domain.GrantRule
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Fan-out de um evento para os destinatarios. As regras "Jose sim, Maria nao"
 * ja rodaram no aparelho; aqui aplicamos so o que o servidor sabe: quais grants
 * estao ativos, qual app cada um liberou, e — quando a regra e por remetente —
 * se o hash bate.
 */
@Service
class EventRouter(
    private val grants: GrantService,
    private val grantRules: GrantRuleRepository,
    private val grantRuleSenders: GrantRuleSenderRepository,
    private val recipientMutes: RecipientAppMuteRepository,
    private val deliveries: EventDeliveryRepository,
    private val users: UserRepository,
    private val pushNotifier: PushNotifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun fanOut(event: Event): Int {
        val active = grants.activeOutbound(event.originUserId)
        if (active.isEmpty()) return 0

        if (event.packageName == Event.PKG_TEST) return fanOutTest(event, active)

        val ruleByGrant = grantRules.findAllByGrantIdIn(active.map { it.id })
            .filter { it.packageName == event.packageName }
            .associateBy { it.grantId }
        if (ruleByGrant.isEmpty()) return 0

        val sendersByRule = grantRuleSenders.findAllByRuleIdIn(ruleByGrant.values.map { it.id })
            .groupBy({ it.ruleId }, { it.senderHash })
            .mapValues { it.value.toSet() }

        val toSave = buildList {
            for (grant in active) {
                val rule = ruleByGrant[grant.id] ?: continue
                if (!passes(rule, event, sendersByRule[rule.id].orEmpty())) continue
                add(
                    EventDelivery(
                        eventId = event.id,
                        grantId = grant.id,
                        recipientId = grant.recipientId,
                        deliveredMode = rule.contentMode,
                    )
                )
            }
        }
        if (toSave.isEmpty()) return 0

        deliveries.saveAll(toSave)
        val originNickname = users.findById(event.originUserId).map { it.nickname }.orElse("")

        // quem recebe pode silenciar um app: a entrega acima ja foi salva (entra
        // no feed), aqui so decidimos se dispara push.
        val mutedGrants = recipientMutes
            .findAllByGrantIdIn(toSave.map { it.grantId }.toSet())
            .filter { it.packageName == event.packageName }
            .map { it.grantId }
            .toSet()

        var pushed = 0
        toSave.forEach {
            if (it.grantId in mutedGrants) return@forEach
            push(event, it, originNickname)
            pushed++
        }
        log.debug("evento {} entregue a {} ({} push)", event.id, toSave.size, pushed)
        return toSave.size
    }

    /**
     * "Enviar notificacao de teste": entrega a TODOS os grants ativos, sem
     * exigir regra e ignorando o mute do destinatario — o objetivo e provar que
     * a entrega funciona, entao ela precisa aparecer de qualquer jeito.
     */
    private fun fanOutTest(event: Event, active: List<com.notifyshare.grants.domain.Grant>): Int {
        val toSave = active.map {
            EventDelivery(
                eventId = event.id,
                grantId = it.id,
                recipientId = it.recipientId,
                deliveredMode = GrantRule.CONTENT,
            )
        }
        if (toSave.isEmpty()) return 0
        deliveries.saveAll(toSave)
        val originNickname = users.findById(event.originUserId).map { it.nickname }.orElse("")
        toSave.forEach { push(event, it, originNickname) }
        log.debug("teste {} entregue a {}", event.id, toSave.size)
        return toSave.size
    }

    private fun passes(rule: GrantRule, event: Event, allowedSenders: Set<String>): Boolean {
        if (!rule.enabled || rule.contentMode == GrantRule.PAUSED) return false
        if (event.eventType == Event.TYPE_MESSAGE && !rule.allSenders) {
            if (event.senderHash == null || event.senderHash !in allowedSenders) return false
        }
        if (event.eventType == Event.TYPE_MESSAGE) {
            val text = messageText(event.content)
            // protecao contra codigo de verificacao (OTP), opt-out por regra
            if (!rule.allowCodes && looksLikeVerificationCode(text)) return false
            // filtro por texto (nome de canal do YouTube etc.)
            val filters = rule.textFilters?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (filters.isNotEmpty() && filters.none { text.contains(it, ignoreCase = true) }) return false
        }
        return true
    }

    /** Junta titulo + corpo do JSON de conteudo. Regex de proposito: nunca faz o
     *  servidor "entender" a mensagem, so olha se ha um codigo ou um termo. */
    private fun messageText(content: String?): String {
        if (content.isNullOrBlank()) return ""
        return Regex("\"(?:title|body)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(content)
            .joinToString(" ") { it.groupValues[1] }
    }

    private val codeKeywords = listOf(
        "codigo", "código", "code", "verification", "verificacao", "verificação",
        "senha", "password", "otp", "token", "2fa", "acesso", "confirmacao",
        "confirmação", "pin", "one-time", "login",
    )

    private fun looksLikeVerificationCode(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val hasKeyword = codeKeywords.any { it in lower }
        if (!hasKeyword) return false
        // um numero de 4 a 8 digitos "solto" (nao parte de um numero maior)
        return Regex("(?<!\\d)\\d{4,8}(?!\\d)").containsMatchIn(text)
    }

    private fun push(event: Event, delivery: EventDelivery, originNickname: String) {
        val data = buildMap {
            put("eventId", event.id.toString())
            put("deliveryId", delivery.id.toString())
            // "from" e chave reservada do FCM em payload de dados — nao usar
            put("origin", originNickname)
            put("packageName", event.packageName)
            put("eventType", event.eventType)
            put("occurredAt", event.occurredAt.toString())
            put("mode", delivery.deliveredMode)
            event.senderHash?.let { put("senderHash", it) }
            // conteudo so quando a regra e "content", e limitado pelo teto de 4KB do FCM
            if (delivery.deliveredMode == GrantRule.CONTENT) {
                event.content?.take(3500)?.let { put("content", it) }
            }
        }
        pushNotifier.notifyUser(delivery.recipientId, PushMessage(type = "event", data = data))
    }
}
