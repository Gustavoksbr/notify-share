package com.notifyshare.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.notifyshare.AppContainer
import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.ui.ChatViewModelFactory
import com.notifyshare.ui.PersonHubViewModelFactory
import com.notifyshare.ui.PersonNotificationsViewModelFactory
import com.notifyshare.ui.RecipientRulesViewModelFactory
import com.notifyshare.ui.RulesViewModelFactory
import com.notifyshare.ui.chat.ChatScreen
import com.notifyshare.ui.chat.ChatViewModel
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.OutlinedActionButton
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.common.StatusDot
import com.notifyshare.ui.common.prettyPackage
import com.notifyshare.ui.format.relativeShort
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.notifications.PersonNotificationsScreen
import com.notifyshare.ui.notifications.PersonNotificationsViewModel
import com.notifyshare.ui.share.RecipientRulesViewModel
import com.notifyshare.ui.share.RuleCard
import com.notifyshare.ui.share.RulesViewModel
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Resolve os grants estabelecidos (nos dois sentidos) com uma pessoa e cuida das
 * acoes de oferecer / pedir compartilhamento. Alimenta a aba "Apps" do hub.
 */
class PersonHubViewModel(
    private val repo: SocialRepository,
    val nickname: String,
) : ViewModel() {

    data class St(
        val loading: Boolean = true,
        /** grant onde EU compartilho com a pessoa (ativo ou pausado). */
        val sharerGrantId: String? = null,
        /** grant onde a pessoa compartilha COMIGO (ativo ou pausado). */
        val recipientGrantId: String? = null,
        val working: Boolean = false,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(St())
    val state: StateFlow<St> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            AppEvents.bus.collect {
                if (it == AppEvents.GRANTS || it == AppEvents.FRIENDS) load(silent = true)
            }
        }
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            when (val r = repo.userProfile(nickname)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    loading = false,
                    sharerGrantId = r.value.sharingWithThem.firstOrNull { it.status in ESTABLISHED }?.id,
                    recipientGrantId = r.value.receivingFromThem.firstOrNull { it.status in ESTABLISHED }?.id,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun offer() = act { repo.offerGrant(nickname) }
    fun request() = act { repo.requestGrant(nickname) }

    private fun act(call: suspend () -> ApiResult<GrantDto>) = viewModelScope.launch {
        _state.value = _state.value.copy(working = true, notice = null)
        val r = call()
        _state.value = _state.value.copy(
            working = false,
            notice = when {
                r is ApiResult.Ok && r.value.status == "active" -> "Compartilhamento ativado."
                r is ApiResult.Ok -> "Pedido enviado para @$nickname."
                r is ApiResult.Failure -> r.message
                else -> null
            },
        )
        load(silent = true)
    }

    fun dismissNotice() { _state.value = _state.value.copy(notice = null) }

    private companion object {
        val ESTABLISHED = setOf("active", "paused_by_sharer", "paused_by_recipient")
    }
}

private enum class HubTab(val label: String) {
    CONVERSA("Conversa"),
    NOTIFICACOES("Notificações"),
    APPS("Apps"),
}

private fun tabFromArg(arg: String?): HubTab = when (arg) {
    "notificacoes" -> HubTab.NOTIFICACOES
    "apps" -> HubTab.APPS
    else -> HubTab.CONVERSA
}

/**
 * Hub centrado na pessoa: um cabecalho unico + 3 abas (Conversa / Notificacoes /
 * Apps). Cada aba reaproveita a tela que ja existe, no modo `embedded`.
 *
 * Os ViewModels de conversa/notificacoes/regras sao presos ao [hubEntry] para
 * sobreviverem a troca de abas sem recarregar do servidor.
 */
@Composable
fun PersonHubScreen(
    container: AppContainer,
    hubEntry: NavBackStackEntry,
    nickname: String,
    initialTab: String?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAppPicker: (grantId: String) -> Unit,
    /** Chegou aqui por "responder notificação" de fora do hub (Feed, notificações
     *  da pessoa) — pre-anexa essa notificação na primeira composição do chat. */
    initialLinkedEvent: String? = null,
    /** Chegou aqui clicando numa notificação (Feed) — realça ela na aba
     *  Notificações assim que abre, igual ao toque num link de notificação
     *  dentro da conversa. */
    initialHighlightEvent: String? = null,
) {
    val hubVm: PersonHubViewModel = viewModel(factory = PersonHubViewModelFactory(container, nickname))
    val hub by hubVm.state.collectAsStateWithLifecycle()

    val chatVm: ChatViewModel = viewModel(
        viewModelStoreOwner = hubEntry,
        key = "hub-chat-$nickname",
        factory = ChatViewModelFactory(container, nickname, initialLinkedEvent),
    )
    val chatState by chatVm.state.collectAsStateWithLifecycle()

    val notifVm: PersonNotificationsViewModel = viewModel(
        viewModelStoreOwner = hubEntry,
        key = "hub-notif-$nickname",
        factory = PersonNotificationsViewModelFactory(container, nickname),
    )

    var tabIndex by rememberSaveable { mutableIntStateOf(tabFromArg(initialTab).ordinal) }
    val tab = HubTab.values()[tabIndex]
    var highlightEvent by remember { mutableStateOf(initialHighlightEvent) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val exportScope = androidx.compose.runtime.rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    val notifState by notifVm.state.collectAsStateWithLifecycle()

    // Exportar e uma escolha em duas etapas: direcao (o que ele manda / o que eu
    // mando / os dois), depois escopo (tudo ou so o que bate com um filtro) —
    // mesma anatomia do fluxo de exportacao de Salvos.
    var showExportDirectionChoice by remember { mutableStateOf(false) }
    var exportDirection by remember { mutableStateOf<String?>(null) }
    var showExportScopeChoice by remember { mutableStateOf(false) }
    var showExportFilters by remember { mutableStateOf(false) }

    fun runExport(direction: String, query: com.notifyshare.data.FeedQuery) {
        exporting = true
        exportScope.launch {
            val directionsToFetch = if (direction == "both") listOf("received", "sent") else listOf(direction)
            val parts = directionsToFetch.map { d ->
                d to container.feedRepository.fullConversation(nickname, d, query)
            }
            exporting = false
            if (parts.all { it.second != null }) {
                val nonNullParts = parts.map { (d, rows) -> d to rows!! }
                val suffix = when (direction) {
                    "received" -> "-recebidas"
                    "sent" -> "-enviadas"
                    else -> ""
                }
                com.notifyshare.ui.common.shareTextExport(
                    context,
                    "notificacoes-$nickname$suffix.json",
                    buildNotificationsExportJson(nonNullParts),
                    mime = "application/json",
                )
            } else {
                android.widget.Toast.makeText(
                    context, "Não deu para exportar agora", android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        ) {
            Icon(
                NotifyIcons.Back, "Voltar",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(12.dp),
            )
            Avatar(
                nickname, size = 36, online = chatState.online,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Column(Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                Text("@$nickname", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StatusDot(chatState.online)
                    Text(
                        if (chatState.online) "Online"
                        else listOf("Offline", relativeShort(chatState.lastSeen))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chatState.online) NotifyShareColors.online else NotifyShareColors.muted,
                    )
                }
            }
            if (tab == HubTab.NOTIFICACOES && !exporting) {
                Icon(
                    NotifyIcons.Export,
                    "Baixar notificações",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { showExportDirectionChoice = true }
                        .padding(8.dp),
                )
            }
        }

        TabRow(selectedTabIndex = tabIndex, containerColor = MaterialTheme.colorScheme.surface) {
            HubTab.values().forEachIndexed { i, t ->
                Tab(
                    selected = tabIndex == i,
                    onClick = {
                        // troca manual de aba nao deve reacender o realce da notificacao
                        if (i != HubTab.NOTIFICACOES.ordinal) highlightEvent = null
                        tabIndex = i
                    },
                    text = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        hub.notice?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { hubVm.dismissNotice() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text("OK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                HubTab.CONVERSA -> ChatScreen(
                    vm = chatVm,
                    nickname = nickname,
                    onBack = onBack,
                    onOpenNotifications = { tabIndex = HubTab.NOTIFICACOES.ordinal },
                    onOpenLinkedNotification = { evId ->
                        highlightEvent = evId
                        tabIndex = HubTab.NOTIFICACOES.ordinal
                    },
                    onOpenProfile = onOpenProfile,
                    embedded = true,
                )
                HubTab.NOTIFICACOES -> PersonNotificationsScreen(
                    vm = notifVm,
                    nickname = nickname,
                    onBack = onBack,
                    highlightEventId = highlightEvent,
                    onReplyToNotification = { row ->
                        chatVm.linkEvent(
                            row.eventId,
                            label = "${prettyPackage(row.packageName)} · ${shortTime(row.occurredAt)}",
                            preview = com.notifyshare.ui.format.eventBody(row),
                        )
                        tabIndex = HubTab.CONVERSA.ordinal
                    },
                    embedded = true,
                )
                HubTab.APPS -> PersonAppsTab(
                    hubEntry = hubEntry,
                    container = container,
                    nickname = nickname,
                    hub = hub,
                    onOffer = hubVm::offer,
                    onRequest = hubVm::request,
                    onOpenAppPicker = onOpenAppPicker,
                )
            }
        }
    }

    if (showExportDirectionChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportDirectionChoice = false },
            title = { Text("O que exportar?") },
            text = { Text("Só o que @$nickname te manda, só o que você manda pra ela, ou os dois?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportDirection = "received"
                    showExportDirectionChoice = false
                    showExportScopeChoice = true
                }) { Text("O que ele/ela manda") }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = {
                        exportDirection = "sent"
                        showExportDirectionChoice = false
                        showExportScopeChoice = true
                    }) { Text("O que eu mando") }
                    androidx.compose.material3.TextButton(onClick = {
                        exportDirection = "both"
                        showExportDirectionChoice = false
                        showExportScopeChoice = true
                    }) { Text("Os dois") }
                }
            },
        )
    }

    if (showExportScopeChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportScopeChoice = false },
            title = { Text("Exportar") },
            text = { Text("Exportar tudo, ou só o que bate com um filtro?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showExportScopeChoice = false
                    exportDirection?.let { runExport(it, com.notifyshare.data.FeedQuery()) }
                }) { Text("Tudo") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showExportScopeChoice = false
                    showExportFilters = true
                }) { Text("Com filtros…") }
            },
        )
    }

    if (showExportFilters) {
        com.notifyshare.ui.common.NotificationFiltersSheet(
            current = com.notifyshare.data.FeedQuery(),
            people = emptyList(),
            packages = notifState.knownPackages,
            confirmLabel = "Exportar",
            onDismiss = { showExportFilters = false },
            onApply = { q ->
                showExportFilters = false
                exportDirection?.let { runExport(it, q) }
            },
            onClear = {
                showExportFilters = false
                exportDirection?.let { runExport(it, com.notifyshare.data.FeedQuery()) }
            },
        )
    }
}

@Composable
private fun PersonAppsTab(
    hubEntry: NavBackStackEntry,
    container: AppContainer,
    nickname: String,
    hub: PersonHubViewModel.St,
    onOffer: () -> Unit,
    onRequest: () -> Unit,
    onOpenAppPicker: (grantId: String) -> Unit,
) {
    if (hub.loading) {
        LoadingBox()
        return
    }

    // Sub-abas horizontais (como em Notificações): uma direção por vez, sem
    // precisar rolar a tela toda pra chegar na de baixo.
    var sub by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Eu envio") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Ele envia") })
        }

        Box(Modifier.weight(1f)) {
            if (sub == 0) {
                val sharerGrantId = hub.sharerGrantId
                if (sharerGrantId != null) {
                    SharerAppsSection(hubEntry, container, sharerGrantId, onOpenAppPicker)
                } else {
                    AppsTabEmpty(
                        "Você ainda não compartilha com @$nickname.",
                        "Oferecer minhas notificações para @$nickname",
                        hub.working, onOffer,
                    )
                }
            } else {
                val recipientGrantId = hub.recipientGrantId
                if (recipientGrantId != null) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 28.dp),
                    ) {
                        RecipientNotifySection(hubEntry, container, recipientGrantId, nickname)
                    }
                } else {
                    AppsTabEmpty(
                        "@$nickname ainda não compartilha com você.",
                        "Pedir para receber as notificações de @$nickname",
                        hub.working, onRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsTabEmpty(text: String, action: String, loading: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = NotifyShareColors.muted)
        OutlinedActionButton(action, onClick = onClick, loading = loading, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SharerAppsSection(
    hubEntry: NavBackStackEntry,
    container: AppContainer,
    grantId: String,
    onOpenAppPicker: (grantId: String) -> Unit,
) {
    val vm: RulesViewModel = viewModel(
        viewModelStoreOwner = hubEntry,
        key = "hub-rules-$grantId",
        factory = RulesViewModelFactory(container, grantId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var contactPickerFor by remember { mutableStateOf<String?>(null) }
    var copyTarget by remember { mutableStateOf<com.notifyshare.ui.share.CopyTarget?>(null) }

    LaunchedEffect(copyTarget) { if (copyTarget != null) vm.loadSourceGrants() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 12.dp),
        ) {
            Text(
                "Adicionar app",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .clickable { onOpenAppPicker(grantId) }
                    .padding(vertical = 6.dp),
            )
            Text(
                "Copiar configuração de outro compartilhamento",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .clickable { copyTarget = com.notifyshare.ui.share.CopyTarget.All }
                    .padding(vertical = 4.dp),
            )

            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                state.rules.isEmpty() -> Text(
                    "Nenhum app liberado ainda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NotifyShareColors.muted,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.rules.forEach { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { vm.toggleEnabled(rule.packageName) },
                            onMode = { vm.setMode(rule.packageName, it) },
                            onAllSenders = { vm.toggleAllSenders(rule.packageName) },
                            onRemove = { vm.removeApp(rule.packageName) },
                            onToggleSender = { h, l -> vm.toggleSender(rule.packageName, h, l) },
                            onAddSender = { name ->
                                vm.toggleSender(rule.packageName, com.notifyshare.notify.hashSender(name), name)
                            },
                            onOpenContactPicker = { contactPickerFor = rule.packageName },
                            onCopyApp = { copyTarget = com.notifyshare.ui.share.CopyTarget.App(rule.packageName) },
                            onCopySenders = { copyTarget = com.notifyshare.ui.share.CopyTarget.Senders(rule.packageName) },
                        )
                    }
                }
            }
        }

        com.notifyshare.ui.share.SaveStatusLine(
            saving = state.saving, error = state.error, onRetry = vm::retrySave,
        )
    }

    copyTarget?.let { target ->
        com.notifyshare.ui.share.CopyConfigDialog(
            target = target,
            grants = state.sourceGrants,
            loading = state.sourceLoading,
            busy = state.copyBusy,
            onDismiss = { copyTarget = null },
            onConfirm = { sourceId, replace ->
                when (target) {
                    is com.notifyshare.ui.share.CopyTarget.All -> vm.copyAllFrom(sourceId, replace)
                    is com.notifyshare.ui.share.CopyTarget.App -> vm.copyAppFrom(target.pkg, sourceId)
                    is com.notifyshare.ui.share.CopyTarget.Senders ->
                        vm.copySendersFrom(target.pkg, sourceId, replace)
                }
                copyTarget = null
            },
        )
    }

    contactPickerFor?.let { pkg ->
        com.notifyshare.ui.share.ContactPickerSheet(
            onDismiss = { contactPickerFor = null },
            onPick = { name ->
                vm.toggleSender(pkg, com.notifyshare.notify.hashSender(name), name)
                contactPickerFor = null
            },
        )
    }
}

@Composable
private fun RecipientNotifySection(
    hubEntry: NavBackStackEntry,
    container: AppContainer,
    grantId: String,
    nickname: String,
) {
    val vm: RecipientRulesViewModel = viewModel(
        viewModelStoreOwner = hubEntry,
        key = "hub-rec-$grantId",
        factory = RecipientRulesViewModelFactory(container, grantId),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Text(
        "Desligar um app não para o compartilhamento — as notificações continuam " +
            "aparecendo no seu feed, só deixam de te avisar na hora.",
        style = MaterialTheme.typography.labelMedium,
        color = NotifyShareColors.muted,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )

    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        state.failedToLoad -> Text(
            "Não foi possível carregar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        state.rules.isEmpty() -> Text(
            "@$nickname ainda não liberou nenhum app.",
            style = MaterialTheme.typography.bodySmall,
            color = NotifyShareColors.muted,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.rules.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    com.notifyshare.ui.common.AppIcon(rule.packageName, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(com.notifyshare.ui.common.appLabel(rule.packageName), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (rule.notify) "Notifica você" else "Só no feed — sem aviso",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotifyShareColors.muted,
                        )
                        com.notifyshare.ui.share.RecipientSendersLine(rule)
                    }
                    Switch(
                        checked = rule.notify,
                        onCheckedChange = { vm.toggleNotify(rule.packageName, it) },
                    )
                }
            }
        }
    }
}

@Serializable
private data class NotificationExportItem(
    /** sent | received */
    val direction: String,
    val app: String,
    val occurredAt: String,
    val title: String?,
    val body: String,
)

@Serializable
private data class NotificationsExport(
    val exportedAt: String,
    val count: Int,
    val items: List<NotificationExportItem>,
)

private val exportJson = Json { prettyPrint = true }

/** Junta as direções escolhidas (sent e/ou received) num JSON só, mais novo primeiro. */
private fun buildNotificationsExportJson(
    parts: List<Pair<String, List<com.notifyshare.data.remote.FeedItemDto>>>,
): String {
    val items = parts
        .flatMap { (direction, rows) -> rows.map { direction to it } }
        .sortedByDescending { it.second.occurredAt }
        .map { (direction, row) ->
            NotificationExportItem(
                direction = direction,
                app = com.notifyshare.ui.format.friendlyPackage(row.packageName),
                occurredAt = row.occurredAt,
                title = com.notifyshare.ui.format.eventTitle(row).takeIf { it.isNotBlank() },
                body = com.notifyshare.ui.format.eventBody(row),
            )
        }
    val payload = NotificationsExport(
        exportedAt = java.time.Instant.now().toString(),
        count = items.size,
        items = items,
    )
    return exportJson.encodeToString(payload)
}
