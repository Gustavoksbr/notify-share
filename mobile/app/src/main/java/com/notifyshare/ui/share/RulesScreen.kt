package com.notifyshare.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.RuleDto
import com.notifyshare.data.remote.SenderDto
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.format.friendlyPackage
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RulesUiState(
    val loading: Boolean = true,
    val rules: List<RuleDto> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedAt: Long = 0,
    val refreshing: Boolean = false,
)

class RulesViewModel(
    private val repo: SocialRepository,
    private val grantId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Puxar para recarregar: ignora se ha edicoes nao salvas, para nao perde-las. */
    fun refresh() {
        if (_state.value.dirty) return
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        _state.value = _state.value.copy(loading = !silent)
        viewModelScope.launch {
            when (val r = repo.rules(grantId)) {
                is ApiResult.Ok -> _state.value = RulesUiState(loading = false, rules = r.value)
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = r.message)
            }
        }
    }

    private fun mutate(pkg: String, block: (RuleDto) -> RuleDto) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.map { if (it.packageName == pkg) block(it) else it },
            dirty = true,
        )
    }

    fun toggleEnabled(pkg: String) = mutate(pkg) { it.copy(enabled = !it.enabled) }
    fun setMode(pkg: String, mode: String) = mutate(pkg) { it.copy(contentMode = mode) }
    fun toggleAllSenders(pkg: String) = mutate(pkg) { it.copy(allSenders = !it.allSenders) }
    fun removeApp(pkg: String) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.filterNot { it.packageName == pkg },
            dirty = true,
        )
    }

    fun addApp(pkg: String) {
        if (_state.value.rules.any { it.packageName == pkg }) return
        _state.value = _state.value.copy(
            rules = _state.value.rules + RuleDto(packageName = pkg),
            dirty = true,
        )
    }

    fun toggleSender(pkg: String, hash: String, label: String?) = mutate(pkg) { rule ->
        val exists = rule.senders.any { it.senderHash == hash }
        rule.copy(
            senders = if (exists) rule.senders.filterNot { it.senderHash == hash }
            else rule.senders + SenderDto(hash, label),
        )
    }

    fun save() {
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            when (val r = repo.setRules(grantId, _state.value.rules)) {
                is ApiResult.Ok -> _state.value = RulesUiState(
                    loading = false, rules = r.value, dirty = false, savedAt = System.currentTimeMillis(),
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }
}

@Composable
fun RulesScreen(
    vm: RulesViewModel,
    nickname: String,
    onBack: () -> Unit,
    onPickApps: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        val activeCount = state.rules.count { it.enabled }
        ScreenTitle(
            if (activeCount > 0) "Apps para @$nickname · $activeCount" else "Apps para @$nickname",
            onBack = onBack,
            trailing = {
                Icon(
                    NotifyIcons.Plus, "Adicionar app",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onPickApps).padding(12.dp),
                )
            },
        )

        when {
            state.loading -> LoadingBox()
            state.rules.isEmpty() -> EmptyState("Nenhum app liberado ainda.\nToque no + para escolher.")
            else -> com.notifyshare.ui.common.PullRefresh(
                state.refreshing, vm::refresh, Modifier.weight(1f),
            ) {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.rules, key = { it.packageName }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { vm.toggleEnabled(rule.packageName) },
                        onMode = { vm.setMode(rule.packageName, it) },
                        onAllSenders = { vm.toggleAllSenders(rule.packageName) },
                        onRemove = { vm.removeApp(rule.packageName) },
                    )
                }
              }
            }
        }

        if (state.dirty) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = androidx.compose.ui.Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp))
                    .clickable(enabled = !state.saving) { vm.save() }
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    "Salvar regras",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.alpha(if (state.saving) 0f else 1f),
                )
                if (state.saving) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleDto,
    onToggle: () -> Unit,
    onMode: (String) -> Unit,
    onAllSenders: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.notifyshare.ui.common.AppIcon(rule.packageName, size = 32.dp)
            Text(
                friendlyPackage(rule.packageName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }

        if (rule.enabled) {
            SegmentedRow(
                options = listOf("content" to "Conteúdo", "sender_only" to "Só remetente", "paused" to "Pausado"),
                selected = rule.contentMode,
                onSelect = onMode,
            )

            val isMessagingApp = !rule.packageName.startsWith("system:")
            if (isMessagingApp && rule.contentMode != "paused") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Todos os remetentes",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = rule.allSenders, onCheckedChange = { onAllSenders() })
                }
                if (!rule.allSenders) {
                    SectionLabel("Remetentes liberados")
                    if (rule.senders.isEmpty()) {
                        Text(
                            "A lista cresce conforme você recebe mensagens desse app.",
                            style = MaterialTheme.typography.labelMedium,
                            color = NotifyShareColors.muted,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rule.senders.forEach { s ->
                                Chip(s.senderLabel ?: s.senderHash.take(6), selected = true) {}
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Remover",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(15.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
