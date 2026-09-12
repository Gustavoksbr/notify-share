package com.notifyshare.ui.share

import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.local.VaultStore
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.data.remote.RuleDto

/**
 * De onde a tela de regras lê e grava. Duas fontes: um compartilhamento no
 * servidor, ou o cofre local (offline, sem login). A UI (RulesScreen, RuleCard,
 * AppPickerScreen) é a mesma para os dois.
 */
interface RulesBackend {
    suspend fun load(): ApiResult<List<RuleDto>>
    suspend fun save(rules: List<RuleDto>): ApiResult<List<RuleDto>>
    /** Compartilhamentos dos quais dá para "copiar config". Vazio se indisponível. */
    suspend fun copySources(): List<GrantDto>
    suspend fun rulesOf(grantId: String): List<RuleDto>?
    fun onSaved()
    /** true = puxar-para-recarregar faz sentido (fonte remota). */
    val supportsRefresh: Boolean
}

class GrantRulesBackend(
    private val repo: SocialRepository,
    private val grantId: String,
) : RulesBackend {
    override suspend fun load() = repo.rules(grantId)
    override suspend fun save(rules: List<RuleDto>) = repo.setRules(grantId, rules)
    override suspend fun copySources(): List<GrantDto> =
        (repo.grants("sharer") as? ApiResult.Ok)?.value.orEmpty().filter { it.id != grantId }
    override suspend fun rulesOf(grantId: String): List<RuleDto>? =
        (repo.rules(grantId) as? ApiResult.Ok)?.value
    override fun onSaved() = AppEvents.signal(AppEvents.GRANTS)
    override val supportsRefresh = true
}

class VaultRulesBackend(
    private val vault: VaultStore,
    /** null quando deslogado — aí "copiar config" fica vazio. */
    private val repo: SocialRepository?,
) : RulesBackend {
    override suspend fun load() = ApiResult.Ok(vault.rulesNow())
    override suspend fun save(rules: List<RuleDto>): ApiResult<List<RuleDto>> {
        vault.setRules(rules)
        return ApiResult.Ok(rules)
    }
    override suspend fun copySources(): List<GrantDto> =
        repo?.let { (it.grants("sharer") as? ApiResult.Ok)?.value }.orEmpty()
    override suspend fun rulesOf(grantId: String): List<RuleDto>? =
        repo?.let { (it.rules(grantId) as? ApiResult.Ok)?.value }
    override fun onSaved() = Unit
    override val supportsRefresh = false
}
