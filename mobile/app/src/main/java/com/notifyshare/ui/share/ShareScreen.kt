package com.notifyshare.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShareUiState(
    val loading: Boolean = true,
    val sharing: List<GrantDto> = emptyList(),
    val receiving: List<GrantDto> = emptyList(),
    val incoming: List<GrantDto> = emptyList(),
    val outgoing: List<GrantDto> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    /** amigos, para o seletor de "novo compartilhamento" */
    val friends: List<String> = emptyList(),
    val notice: String? = null,
)

class ShareViewModel(private val repo: SocialRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        load()
        loadFriends()
        viewModelScope.launch {
            AppEvents.bus.collect { if (it == AppEvents.GRANTS || it == AppEvents.FRIENDS) load(silent = true) }
        }
    }

    private fun loadFriends() = viewModelScope.launch {
        (repo.friends() as? ApiResult.Ok)?.value?.let { list ->
            _state.value = _state.value.copy(friends = list.map { it.nickname })
        }
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true, error = null)
        val prev = _state.value
        viewModelScope.launch {
            val sharer = repo.grants("sharer")
            val recipient = repo.grants("recipient")
            val pending = repo.pendingGrants()
            _state.value = ShareUiState(
                loading = false,
                refreshing = false,
                sharing = (sharer as? ApiResult.Ok)?.value.orEmpty(),
                receiving = (recipient as? ApiResult.Ok)?.value.orEmpty(),
                incoming = (pending as? ApiResult.Ok)?.value?.incoming.orEmpty(),
                outgoing = (pending as? ApiResult.Ok)?.value?.outgoing.orEmpty(),
                error = (sharer as? ApiResult.Failure)?.message,
                friends = prev.friends,
                notice = prev.notice,
            )
        }
    }

    fun offerTo(nickname: String) = grantAction("Oferta enviada para @$nickname.") { repo.offerGrant(nickname) }
    fun requestFrom(nickname: String) = grantAction("Pedido enviado para @$nickname.") { repo.requestGrant(nickname) }

    private fun grantAction(okMsg: String, call: suspend () -> ApiResult<GrantDto>) = viewModelScope.launch {
        val r = call()
        val msg = when {
            r is ApiResult.Ok && r.value.status == "active" -> "Compartilhamento ativado."
            r is ApiResult.Ok -> okMsg
            r is ApiResult.Failure -> r.message
            else -> null
        }
        _state.value = _state.value.copy(notice = msg)
        load(silent = true)
    }

    fun dismissNotice() { _state.value = _state.value.copy(notice = null) }

    fun togglePause(g: GrantDto) = viewModelScope.launch {
        if (g.isPausedByMe) repo.resumeGrant(g.id) else repo.pauseGrant(g.id)
        load(silent = true)
    }

    fun accept(id: String) = viewModelScope.launch { repo.acceptGrant(id); load(silent = true) }
    fun decline(id: String) = viewModelScope.launch { repo.declineGrant(id); load(silent = true) }
    fun revoke(id: String) = viewModelScope.launch { repo.revokeGrant(id); load(silent = true) }
}

@Composable
fun ShareScreen(
    vm: ShareViewModel,
    onOpenRequests: () -> Unit,
    onOpenRules: (grantId: String, nickname: String) -> Unit,
    onOpenNotifyRules: (grantId: String, nickname: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    val pendingCount = state.incoming.size + state.outgoing.size

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Compartilhamentos", trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pendingCount > 0) {
                    Text(
                        "$pendingCount pedidos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onOpenRequests)
                            .padding(12.dp),
                    )
                }
                Icon(
                    NotifyIcons.Plus, "Novo compartilhamento",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { picker = true }.padding(12.dp),
                )
            }
        })

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Compartilho com") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Recebo de") })
        }

        state.notice?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { vm.dismissNotice() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(msg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("OK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        when {
            state.loading -> LoadingBox()
            state.error != null && state.sharing.isEmpty() && state.receiving.isEmpty() ->
                com.notifyshare.ui.common.ErrorRetry(
                    "Não foi possível carregar seus compartilhamentos.",
                    { vm.load() },
                )
            else -> {
                val list = if (tab == 0) state.sharing else state.receiving
                com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh) {
                    if (list.isEmpty()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                EmptyState(
                                    if (tab == 0) "Você ainda não compartilha com ninguém."
                                    else "Ninguém compartilha com você ainda.",
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list, key = { it.id }) { g ->
                                GrantRow(
                                    g = g,
                                    isSharer = tab == 0,
                                    onToggle = { vm.togglePause(g) },
                                    onClick = {
                                        if (tab == 0) onOpenRules(g.id, g.counterpart)
                                        else onOpenNotifyRules(g.id, g.counterpart)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (picker) {
        val already = if (tab == 0) state.sharing.map { it.counterpart }.toSet()
        else state.receiving.map { it.counterpart }.toSet()
        NewShareDialog(
            request = tab == 1,
            friends = state.friends.filterNot { it in already },
            onDismiss = { picker = false },
            onPick = { nick ->
                picker = false
                if (tab == 1) vm.requestFrom(nick) else vm.offerTo(nick)
            },
        )
    }
}

@Composable
private fun NewShareDialog(
    request: Boolean,
    friends: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (request) "Pedir para receber notificações"
                else "Oferecer minhas notificações",
            )
        },
        text = {
            if (friends.isEmpty()) {
                Text("Nenhum amigo disponível. Adicione amigos na aba Amigos primeiro.")
            } else {
                LazyColumn {
                    items(friends, key = { it }) { nick ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(nick) }
                                .padding(vertical = 10.dp),
                        ) {
                            Avatar(nick, size = 36)
                            Text("@$nick", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun GrantRow(g: GrantDto, isSharer: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(g.counterpart, size = 42)
            Column(Modifier.weight(1f)) {
                Text("@${g.counterpart}", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        g.isPausedByMe -> "Pausado por você"
                        !g.isActive -> "Pausado"
                        isSharer -> "${g.enabledApps} apps" +
                            if (g.hasSpecificSenders) " · remetentes específicos" else ""
                        else -> "${g.enabledApps} apps"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = NotifyShareColors.muted,
                )
            }
            Switch(checked = g.isActive, onCheckedChange = { onToggle() })
        }
    }
}

// --- Pedidos (caixa de pedidos de grant) -------------------------------------

@Composable
fun RequestsScreen(vm: ShareViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Pedidos", onBack = onBack)

        com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.incoming.isNotEmpty()) {
                item { SectionLabel("Recebidos · ${state.incoming.size}") }
                items(state.incoming, key = { it.id }) { g ->
                    PendingCard(
                        title = if (g.role == "sharer") "@${g.counterpart} quer receber suas notificações"
                        else "@${g.counterpart} ofereceu compartilhar com você",
                        onAccept = { vm.accept(g.id) },
                        onDecline = { vm.decline(g.id) },
                    )
                }
            }
            if (state.outgoing.isNotEmpty()) {
                item { SectionLabel("Enviados · ${state.outgoing.size}") }
                items(state.outgoing, key = { it.id }) { g ->
                    PendingCard(
                        title = if (g.role == "sharer") "Você ofereceu compartilhar com @${g.counterpart}"
                        else "Você pediu para receber de @${g.counterpart}",
                        onAccept = null,
                        onDecline = { vm.decline(g.id) },
                    )
                }
            }
            if (state.incoming.isEmpty() && state.outgoing.isEmpty()) {
                item { EmptyState("Nenhum pedido pendente.") }
            }
        }
        }
    }
}

@Composable
private fun PendingCard(title: String, onAccept: (() -> Unit)?, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDecline) {
                Text(if (onAccept == null) "Cancelar" else "Recusar", color = NotifyShareColors.muted)
            }
            if (onAccept != null) {
                Text(
                    "Aceitar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                        .clickable(onClick = onAccept)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }
}
