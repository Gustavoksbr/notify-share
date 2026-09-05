package com.notifyshare.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.FriendDto
import com.notifyshare.data.remote.FriendRequestDto
import com.notifyshare.data.remote.SearchResultDto
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.InlineActionButton
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.PillButton
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<SearchResultDto> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
    val friends: List<FriendDto> = emptyList(),
    val error: String? = null,
    val failedToLoad: Boolean = false,
    val refreshing: Boolean = false,
    /** Nicknames com quem ja existe um compartilhamento (qualquer sentido). */
    val shareGrants: Set<String> = emptySet(),
    /** Acoes em curso: "add:<nick>", "accept:<id>", "decline:<id>". */
    val busy: Set<String> = emptySet(),
)

class FriendsViewModel(private val repo: SocialRepository) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            AppEvents.bus.collect {
                if (it == AppEvents.FRIENDS || it == AppEvents.PRESENCE) load(silent = true)
            }
        }
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val friends = repo.friends()
            val requests = repo.friendRequests()
            val friendsOk = friends as? ApiResult.Ok
            val sharer = repo.grants("sharer")
            val recipient = repo.grants("recipient")
            val anyGrantOk = sharer is ApiResult.Ok || recipient is ApiResult.Ok
            val grantNicks = buildSet {
                (sharer as? ApiResult.Ok)?.value?.forEach { add(it.counterpart) }
                (recipient as? ApiResult.Ok)?.value?.forEach { add(it.counterpart) }
            }
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                friends = friendsOk?.value ?: _state.value.friends,
                incoming = (requests as? ApiResult.Ok)?.value?.incoming ?: _state.value.incoming,
                outgoing = (requests as? ApiResult.Ok)?.value?.outgoing ?: _state.value.outgoing,
                shareGrants = if (anyGrantOk) grantNicks else _state.value.shareGrants,
                error = (friends as? ApiResult.Failure)?.message,
                // "falhou ao carregar" e diferente de "nao tem amigos": so mostra
                // o estado vazio quando o servidor respondeu de fato.
                failedToLoad = friendsOk == null && _state.value.friends.isEmpty(),
            )
        }
    }

    fun onQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.trim().isEmpty()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false, searched = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            _state.value = _state.value.copy(searching = true)
            when (val r = repo.search(q.trim())) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(results = r.value, searching = false, searched = true)
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(searching = false, searched = false)
            }
        }
    }

    fun add(
        nickname: String,
        alsoOfferShare: Boolean = false,
        alsoRequestShare: Boolean = false,
    ) = launchBusy("add:$nickname") {
        repo.addFriend(nickname, alsoOfferShare, alsoRequestShare)
        onQuery(_state.value.query)
        load(silent = true)
        signalSocial()
    }

    fun accept(id: String) = launchBusy("accept:$id") {
        repo.acceptFriend(id); load(silent = true); signalSocial()
    }
    fun decline(id: String) = launchBusy("decline:$id") {
        repo.declineFriend(id); load(silent = true); signalSocial()
    }
    fun cancel(id: String) = launchBusy("cancel:$id") {
        repo.cancelFriendRequest(id)
        onQuery(_state.value.query)
        load(silent = true)
        signalSocial()
    }

    /** Amizade e compartilhamento andam juntos (pedido com share) — avisa os dois. */
    private fun signalSocial() {
        com.notifyshare.core.AppEvents.signal(com.notifyshare.core.AppEvents.FRIENDS)
        com.notifyshare.core.AppEvents.signal(com.notifyshare.core.AppEvents.GRANTS)
    }

    private fun launchBusy(key: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = it.busy + key) }
        try {
            block()
        } finally {
            _state.update { it.copy(busy = it.busy - key) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    vm: FriendsViewModel,
    onOpenRequests: () -> Unit,
    /** Abre o hub da pessoa numa aba: "conversa" | "notificacoes" | "apps". */
    onOpenHub: (nickname: String, tab: String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var addTarget by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Amigos")

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            placeholder = { Text("Buscar por nickname") },
            leadingIcon = { Icon(NotifyIcons.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (state.query.trim().isNotEmpty()) {
            Text(
                when {
                    state.searching -> "Buscando…"
                    state.searched -> "${state.results.size} encontrado${if (state.results.size == 1) "" else "s"}"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.muted,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
            )
        }

        if (state.loading) {
            LoadingBox()
            return@Column
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.results.isNotEmpty()) {
                item { SectionLabel("Resultado da busca") }
                items(state.results, key = { "search_${it.nickname}" }) { r ->
                    val outgoingId = state.outgoing.firstOrNull { it.nickname == r.nickname }?.id
                    SearchRow(
                        r,
                        busy = "add:${r.nickname}" in state.busy ||
                            (outgoingId != null && "cancel:$outgoingId" in state.busy),
                        // "none" abre o diálogo com as opções de já compartilhar;
                        // "request_received" só aceita (a intenção é de quem pediu).
                        onAdd = {
                            if (r.relation == "request_received") vm.add(r.nickname)
                            else addTarget = r.nickname
                        },
                        onCancel = outgoingId?.let { { vm.cancel(it) } },
                        onOpen = { onOpenProfile(r.nickname) },
                    )
                }
            }

            if (state.outgoing.isNotEmpty()) {
                item { SectionLabel("Pedidos enviados · ${state.outgoing.size}") }
                items(state.outgoing, key = { "out_${it.id}" }) { req ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Avatar(req.nickname, size = 40)
                        Column(Modifier.weight(1f)) {
                            Text("@${req.nickname}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Aguardando resposta",
                                style = MaterialTheme.typography.labelSmall,
                                color = NotifyShareColors.muted,
                            )
                        }
                        InlineActionButton(
                            "Cancelar",
                            { vm.cancel(req.id) },
                            loading = "cancel:${req.id}" in state.busy,
                        )
                    }
                }
            }

            if (state.incoming.isNotEmpty()) {
                item { SectionLabel("Pedidos de amizade · ${state.incoming.size}") }
                items(state.incoming, key = { "req_${it.id}" }) { req ->
                    RequestRow(
                        req,
                        accepting = "accept:${req.id}" in state.busy,
                        declining = "decline:${req.id}" in state.busy,
                        onAccept = { vm.accept(req.id) },
                        onDecline = { vm.decline(req.id) },
                    )
                }
            }

            val onlineCount = state.friends.count { it.online }
            item {
                SectionLabel(
                    if (state.failedToLoad) "Meus amigos"
                    else buildString {
                        append("Meus amigos · ${state.friends.size}")
                        if (onlineCount > 0) append(" · $onlineCount online")
                    },
                )
            }
            if (state.friends.isEmpty()) {
                item {
                    if (state.failedToLoad) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Não foi possível carregar seus amigos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NotifyShareColors.muted,
                            )
                            TextButton(onClick = { vm.load() }) { Text("Tentar de novo") }
                        }
                    } else {
                        EmptyState("Você ainda não tem amigos por aqui.")
                    }
                }
            } else {
                items(state.friends, key = { "friend_${it.nickname}" }) { f ->
                    FriendRow(
                        f,
                        hasShare = f.nickname in state.shareGrants,
                        onOpenTab = { tab -> onOpenHub(f.nickname, tab) },
                        onAvatar = { onOpenProfile(f.nickname) },
                    )
                }
            }
        }
        }
    }

    addTarget?.let { nick ->
        AddFriendDialog(
            nickname = nick,
            onDismiss = { addTarget = null },
            onConfirm = { offer, request ->
                vm.add(nick, offer, request)
                addTarget = null
            },
        )
    }
}

@Composable
private fun AddFriendDialog(
    nickname: String,
    onDismiss: () -> Unit,
    onConfirm: (alsoOfferShare: Boolean, alsoRequestShare: Boolean) -> Unit,
) {
    var offer by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar @$nickname") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Quando @$nickname aceitar, já deixar encaminhado:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NotifyShareColors.muted,
                )
                CheckRow("Quero receber as notificações de @$nickname", request) { request = it }
                CheckRow("Quero compartilhar as minhas com @$nickname", offer) { offer = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(offer, request) }) { Text("Enviar pedido") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchRow(
    r: SearchResultDto,
    busy: Boolean,
    onAdd: () -> Unit,
    onCancel: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        Avatar(r.nickname)
        Column(Modifier.weight(1f)) {
            Text("@${r.nickname}", style = MaterialTheme.typography.titleMedium)
            Text(
                when (r.relation) {
                    "friend" -> "Já são amigos"
                    "request_sent" -> "Pedido enviado"
                    "request_received" -> "Pediu para te adicionar"
                    else -> if (r.mutualFriends > 0) "${r.mutualFriends} amigos em comum" else "Sem conexão"
                },
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.muted,
            )
        }
        when {
            r.relation == "none" || r.relation == "request_received" -> PillButton(
                if (r.relation == "request_received") "Aceitar" else "Adicionar",
                onAdd,
                loading = busy,
            )
            r.relation == "request_sent" && onCancel != null ->
                InlineActionButton("Cancelar", onCancel, loading = busy)
        }
    }
}

@Composable
private fun RequestRow(
    req: FriendRequestDto,
    accepting: Boolean,
    declining: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Avatar(req.nickname, size = 40)
        Text("@${req.nickname}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        InlineActionButton("Recusar", onDecline, loading = declining)
        PillButton("Aceitar", onAccept, loading = accepting, enabled = !declining)
    }
}

@Composable
private fun FriendRow(
    f: FriendDto,
    hasShare: Boolean,
    onOpenTab: (tab: String) -> Unit,
    onAvatar: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTab("conversa") }
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Avatar(f.nickname, size = 40, online = f.online, modifier = Modifier.clickable(onClick = onAvatar))
        Column(Modifier.weight(1f)) {
            Text("@${f.nickname}", style = MaterialTheme.typography.titleMedium)
            Text(
                if (f.online) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (f.online) NotifyShareColors.online else NotifyShareColors.muted,
            )
        }
        RowIcon(NotifyIcons.Chat, "Conversa") { onOpenTab("conversa") }
        RowIcon(NotifyIcons.Bell, "Notificações") { onOpenTab("notificacoes") }
        if (hasShare) {
            RowIcon(NotifyIcons.Sliders, "Apps") { onOpenTab("apps") }
        } else {
            RowIcon(NotifyIcons.Plus, "Compartilhar apps", tint = MaterialTheme.colorScheme.primary) {
                onOpenTab("apps")
            }
        }
    }
}

@Composable
private fun RowIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color = NotifyShareColors.muted,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription,
        tint = tint,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(20.dp),
    )
}
