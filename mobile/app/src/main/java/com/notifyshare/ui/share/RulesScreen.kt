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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.data.remote.RuleDto
import com.notifyshare.data.remote.SenderDto
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.NotificationAccessWarning
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.format.friendlyPackage
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RulesUiState(
    val loading: Boolean = true,
    val rules: List<RuleDto> = emptyList(),
    /** true entre uma edição e ela ficar de fato salva (inclui a espera do
     *  autosave e a chamada de rede em si). */
    val saving: Boolean = false,
    val error: String? = null,
    val savedAt: Long = 0,
    val refreshing: Boolean = false,
    /** "Copiar config de outro compartilhamento": candidatos e estado da cópia. */
    val sourceGrants: List<GrantDto> = emptyList(),
    val sourceLoading: Boolean = false,
    val copyBusy: Boolean = false,
)

/**
 * As regras salvam sozinhas: toda edição agenda um save (debounced, para não
 * disparar uma chamada a cada toque numa sequência rápida) em vez de esperar
 * um botão "Salvar". Como o `replace()` do backend manda a lista inteira toda
 * vez, um save cancelado no meio (por uma edição mais nova) se autocorrige no
 * próximo — nunca fica um estado parcial salvo.
 */
class RulesViewModel(
    private val backend: RulesBackend,
) : ViewModel() {

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        load()
    }

    /** Puxar para recarregar: ignora se há um autosave pendente, para não perdê-lo. */
    fun refresh() {
        if (_state.value.saving || !backend.supportsRefresh) return
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        _state.value = _state.value.copy(loading = !silent)
        viewModelScope.launch {
            when (val r = backend.load()) {
                is ApiResult.Ok -> _state.value = RulesUiState(loading = false, rules = r.value)
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = r.message)
            }
        }
    }

    private fun mutate(pkg: String, block: (RuleDto) -> RuleDto) {
        _state.value = _state.value.copy(
            rules = _state.value.rules.map { if (it.packageName == pkg) block(it) else it },
        )
        scheduleSave()
    }

    fun toggleEnabled(pkg: String) = mutate(pkg) { it.copy(enabled = !it.enabled) }
    fun setMode(pkg: String, mode: String) = mutate(pkg) { it.copy(contentMode = mode) }
    fun toggleAllSenders(pkg: String) = mutate(pkg) { it.copy(allSenders = !it.allSenders) }
    fun toggleAllowCodes(pkg: String) = mutate(pkg) { it.copy(allowCodes = !it.allowCodes) }
    fun addTextFilter(pkg: String, term: String) = mutate(pkg) {
        val t = term.trim()
        if (t.isEmpty() || it.textFilters.any { f -> f.equals(t, ignoreCase = true) }) it
        else it.copy(textFilters = it.textFilters + t)
    }
    fun removeTextFilter(pkg: String, term: String) = mutate(pkg) {
        it.copy(textFilters = it.textFilters.filterNot { f -> f == term })
    }
    fun removeApp(pkg: String) {
        _state.value = _state.value.copy(rules = _state.value.rules.filterNot { it.packageName == pkg })
        scheduleSave()
    }

    fun addApp(pkg: String) {
        if (_state.value.rules.any { it.packageName == pkg }) return
        _state.value = _state.value.copy(rules = _state.value.rules + RuleDto(packageName = pkg))
        scheduleSave()
    }

    fun toggleSender(pkg: String, hash: String, label: String?) = mutate(pkg) { rule ->
        val exists = rule.senders.any { it.senderHash == hash }
        rule.copy(
            senders = if (exists) rule.senders.filterNot { it.senderHash == hash }
            else rule.senders + SenderDto(hash, label),
        )
    }

    // --- copiar config de outro compartilhamento ---------------------------

    /** Carrega os outros grants de sharer, para o diálogo de cópia. */
    fun loadSourceGrants() {
        if (_state.value.sourceGrants.isNotEmpty() || _state.value.sourceLoading) return
        _state.value = _state.value.copy(sourceLoading = true)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sourceLoading = false,
                sourceGrants = backend.copySources(),
            )
        }
    }

    private suspend fun fetchRules(sourceId: String): List<RuleDto>? = backend.rulesOf(sourceId)

    fun copyAllFrom(sourceId: String, replace: Boolean) = copy {
        val src = fetchRules(sourceId) ?: return@copy null
        if (replace) src
        else {
            val byPkg = src.associateBy { it.packageName }
            _state.value.rules.filter { it.packageName !in byPkg } + src
        }
    }

    fun copyAppFrom(pkg: String, sourceId: String) = copy {
        val src = fetchRules(sourceId)?.firstOrNull { it.packageName == pkg } ?: return@copy null
        val exists = _state.value.rules.any { it.packageName == pkg }
        if (exists) _state.value.rules.map {
            if (it.packageName != pkg) it
            else it.copy(
                contentMode = src.contentMode, allSenders = src.allSenders,
                senders = src.senders, textFilters = src.textFilters, allowCodes = src.allowCodes,
            )
        } else _state.value.rules + src
    }

    fun copySendersFrom(pkg: String, sourceId: String, replace: Boolean) = copy {
        val src = fetchRules(sourceId)?.firstOrNull { it.packageName == pkg } ?: return@copy null
        _state.value.rules.map { r ->
            if (r.packageName != pkg) r
            else if (src.allSenders) r.copy(allSenders = true)
            else r.copy(
                allSenders = false,
                senders = if (replace) src.senders
                else (r.senders + src.senders).distinctBy { it.senderHash },
            )
        }
    }

    private fun copy(block: suspend () -> List<RuleDto>?) {
        if (_state.value.copyBusy) return
        _state.value = _state.value.copy(copyBusy = true)
        viewModelScope.launch {
            val newRules = block()
            if (newRules == null) {
                _state.value = _state.value.copy(copyBusy = false, error = "Não foi possível copiar.")
            } else {
                _state.value = _state.value.copy(rules = newRules, copyBusy = false)
                scheduleSave()
            }
        }
    }

    /** Autosave debounced: cada edição cancela o save pendente e agenda outro. */
    private fun scheduleSave() {
        _state.value = _state.value.copy(saving = true, error = null)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            when (val r = backend.save(_state.value.rules)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(
                        rules = r.value, saving = false, error = null,
                        savedAt = System.currentTimeMillis(),
                    )
                    backend.onSaved()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    /** Botão "tentar de novo" depois de uma falha de autosave. */
    fun retrySave() {
        if (_state.value.error != null) scheduleSave()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}

@Composable
fun RulesScreen(
    vm: RulesViewModel,
    nickname: String,
    onBack: (() -> Unit)? = null,
    onPickApps: () -> Unit,
    onOpenSystemAlerts: () -> Unit = {},
    title: String? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var contactPickerFor by remember { mutableStateOf<String?>(null) }
    var copyTarget by remember { mutableStateOf<CopyTarget?>(null) }

    LaunchedEffect(copyTarget) { if (copyTarget != null) vm.loadSourceGrants() }

    Column(Modifier.fillMaxSize()) {
        val activeCount = state.rules.count { it.enabled }
        ScreenTitle(
            title ?: if (activeCount > 0) "Apps para @$nickname · $activeCount" else "Apps para @$nickname",
            onBack = onBack,
            trailing = {
                Icon(
                    NotifyIcons.Plus, "Adicionar app",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onPickApps).padding(12.dp),
                )
            },
        )

        NotificationAccessWarning()

        Text(
            "Copiar configuração de outro compartilhamento",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { copyTarget = CopyTarget.All }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            when {
                state.loading -> LoadingBox()
                state.rules.isEmpty() -> EmptyState("Nenhum app liberado ainda.\nToque no + para escolher.")
                else -> com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh) {
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
                            onToggleSender = { h, l -> vm.toggleSender(rule.packageName, h, l) },
                            onAddSender = { name ->
                                vm.toggleSender(
                                    rule.packageName,
                                    com.notifyshare.notify.hashSender(name),
                                    name,
                                )
                            },
                            onOpenContactPicker = { contactPickerFor = rule.packageName },
                            onCopyApp = { copyTarget = CopyTarget.App(rule.packageName) },
                            onCopySenders = { copyTarget = CopyTarget.Senders(rule.packageName) },
                            onOpenSystemAlerts = onOpenSystemAlerts,
                        )
                    }
                  }
                }
            }
        }

        SaveStatusLine(saving = state.saving, error = state.error, onRetry = vm::retrySave)
    }

    copyTarget?.let { target ->
        CopyConfigDialog(
            target = target,
            grants = state.sourceGrants,
            loading = state.sourceLoading,
            busy = state.copyBusy,
            onDismiss = { copyTarget = null },
            onConfirm = { sourceId, replace ->
                when (target) {
                    is CopyTarget.All -> vm.copyAllFrom(sourceId, replace)
                    is CopyTarget.App -> vm.copyAppFrom(target.pkg, sourceId)
                    is CopyTarget.Senders -> vm.copySendersFrom(target.pkg, sourceId, replace)
                }
                copyTarget = null
            },
        )
    }

    contactPickerFor?.let { pkg ->
        ContactPickerSheet(
            onDismiss = { contactPickerFor = null },
            onPick = { name ->
                vm.toggleSender(pkg, com.notifyshare.notify.hashSender(name), name)
                contactPickerFor = null
            },
        )
    }
}

/**
 * As regras salvam sozinhas (ver RulesViewModel.scheduleSave) — aqui só um
 * status discreto: nada visível quando está tudo salvo, "Salvando…" durante o
 * autosave, e um aviso com "Tentar de novo" se a chamada falhar.
 */
@Composable
internal fun SaveStatusLine(saving: Boolean, error: String?, onRetry: () -> Unit) {
    if (!saving && error == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        when {
            error != null -> {
                Text(
                    "Não foi possível salvar. $error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Tentar de novo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
            saving -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = NotifyShareColors.muted,
                )
                Text("Salvando…", style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.muted)
            }
        }
    }
}

@Composable
internal fun RuleCard(
    rule: RuleDto,
    onToggle: () -> Unit,
    onMode: (String) -> Unit,
    onAllSenders: () -> Unit,
    onRemove: () -> Unit,
    onToggleSender: (hash: String, label: String?) -> Unit = { _, _ -> },
    onAddSender: (name: String) -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onCopyApp: (() -> Unit)? = null,
    onCopySenders: (() -> Unit)? = null,
    onOpenSystemAlerts: (() -> Unit)? = null,
) {
    val isSystemPhone = rule.packageName == com.notifyshare.data.local.PKG_SYSTEM_PHONE
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
                com.notifyshare.ui.common.appLabel(rule.packageName),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }

        if (!rule.enabled) {
            Text(
                "Desligado — não é compartilhado. A configuração fica salva.",
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
            )
        }

        if (isSystemPhone) {
            if (rule.enabled) {
                Text(
                    "Alertas do próprio aparelho: bateria, carregador, reinício. " +
                        "Você escolhe quais são enviados.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NotifyShareColors.muted,
                )
                onOpenSystemAlerts?.let { cb ->
                    Text(
                        "Configurar alertas do celular",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = cb).padding(vertical = 4.dp),
                    )
                }
            }
        } else if (rule.enabled) {
            SegmentedRow(
                options = listOf("content" to "Conteúdo", "sender_only" to "Só aviso"),
                selected = if (rule.contentMode == "paused") "content" else rule.contentMode,
                onSelect = onMode,
            )
            Text(
                if (rule.contentMode == "sender_only")
                    "Manda só que chegou algo, sem o texto."
                else
                    "Manda o texto da notificação.",
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
            )

            // Escolher remetente so existe pros apps que a gente sabe que tem essa
            // nocao (ver AppCapabilities) — nao e todo app que "tem contato".
            val capability = com.notifyshare.notify.AppCapabilities.of(rule.packageName)
            if (capability.supportsSenders && rule.contentMode != "paused") {
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
                    Text(
                        "Só os marcados são avisados. Toque num para remover.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NotifyShareColors.muted,
                    )
                    onCopySenders?.let { cb ->
                        Text(
                            "Copiar contatos de outro compartilhamento",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = cb).padding(vertical = 4.dp),
                        )
                    }
                    if (rule.senders.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rule.senders.forEach { s ->
                                Chip(s.senderLabel ?: s.senderHash.take(6), selected = true) {
                                    onToggleSender(s.senderHash, s.senderLabel)
                                }
                            }
                        }
                    }
                    when (capability.senderPicker) {
                        com.notifyshare.notify.SenderPickerType.CONTACTS -> ContactPickerLink(onOpenContactPicker)
                        com.notifyshare.notify.SenderPickerType.FREE_TEXT ->
                            AddTermField("Adicionar remetente", onAddSender)
                        com.notifyshare.notify.SenderPickerType.CONTACTS_AND_TEXT -> {
                            ContactPickerLink(onOpenContactPicker)
                            Text(
                                "OBS: só funciona se o nome marcado aqui for exatamente o texto que aparece na sua notificação. " +
                                    "Em algumas situações — contas comerciais, contatos recém-salvos — o WhatsApp mostra um texto diferente, " +
                                    "e aí o remetente não é reconhecido.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NotifyShareColors.muted,
                            )
                            AddTermField("Nome como aparece na notificação", onAddSender)
                        }
                        com.notifyshare.notify.SenderPickerType.NONE -> Unit
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            onCopyApp?.takeIf { !isSystemPhone }?.let { cb ->
                Text(
                    "Copiar de…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = cb),
                )
            }
            Text(
                "Remover",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun ContactPickerLink(onClick: () -> Unit) {
    Text(
        "Escolher da lista de contato",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun AddTermField(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        )
        Text(
            "Adicionar",
            style = MaterialTheme.typography.labelMedium,
            color = if (text.isBlank()) NotifyShareColors.muted else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(enabled = text.isNotBlank()) { onAdd(text.trim()); text = "" }
                .padding(8.dp),
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
