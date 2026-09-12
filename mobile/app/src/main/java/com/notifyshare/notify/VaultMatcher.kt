package com.notifyshare.notify

import com.notifyshare.data.remote.RuleDto

/**
 * Avalia as regras do cofre local no aparelho — o mesmo que o EventRouter faz no
 * servidor para os compartilhamentos, só que aqui roda offline e sem login.
 * Mantém a lógica em sincronia com `EventRouter.passes()` do backend.
 */
object VaultMatcher {

    /** Retorna a regra que casou (para saber o modo), ou null se nada captura. */
    fun match(
        rules: List<RuleDto>,
        packageName: String,
        eventType: String,
        senderHash: String?,
        text: String,
    ): RuleDto? {
        val rule = rules.firstOrNull { it.packageName == packageName } ?: return null
        if (!rule.enabled || rule.contentMode == "paused") return null

        if (eventType == "message") {
            if (!rule.allSenders) {
                if (senderHash == null || rule.senders.none { it.senderHash == senderHash }) return null
            }
            if (!rule.allowCodes && looksLikeVerificationCode(text)) return null
            val filters = rule.textFilters.map { it.trim() }.filter { it.isNotEmpty() }
            if (filters.isNotEmpty() && filters.none { text.contains(it, ignoreCase = true) }) return null
        }
        return rule
    }

    private val codeKeywords = listOf(
        "codigo", "código", "code", "verification", "verificacao", "verificação",
        "senha", "password", "otp", "token", "2fa", "acesso", "confirmacao",
        "confirmação", "pin", "one-time", "login",
    )

    private val looseCode = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    private fun looksLikeVerificationCode(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        if (codeKeywords.none { it in lower }) return false
        return looseCode.containsMatchIn(text)
    }
}
