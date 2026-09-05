package com.notifyshare.ui.share

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    /** true depois da primeira resposta OK de /friends. */
    val friendsLoaded: Boolean = false,
    /** true quando /friends falhou e nunca carregou — a UI mostra "tente de novo". */
    val friendsError: Boolean = false,
    val notice: String? = null,
    /** Quando != null, o aviso ganha um atalho "Ver notificações de @X". */
    val noticeActionNick: String? = null,
    /** Acoes em curso: "offer:<nick>", "request:<nick>", "accept:<id>", "decline:<id>", "revoke:<id>", "pause:<id>". */
    val busy: Set<String> = emptySet(),
)

class ShareViewModel(private val repo: SocialRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var friendsJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            AppEvents.bus.collect { if (it == AppEvents.GRANTS || it == AppEvents.FRIENDS) load(silent = true) }
        }
    }

    /**
     * Carrega a lista de amigos com retry. O plano free da Render hiberna depois
     * de ociosa, e a primeira chamada ao voltar estoura o timeout — sem retry, a
     * lista ficava vazia ate o usuario sair e voltar da tela, e o seletor de
     * "novo compartilhamento" dizia "nenhum amigo" mesmo tendo amigos.
     */
    private fun refreshFriends() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            repeat(5) { attempt ->
                when (val r = repo.friends()) {
                    is ApiResult.Ok -> {
                        _state.value = _state.value.copy(
                            friends = r.value.map { it.nickname },
                            friendsLoaded = true,
                            friendsError = false,
                        )
                        return@launch
                    }
                    is ApiResult.Failure -> {
                        if (!_state.value.friendsLoaded) {
                            _state.value = _state.value.copy(friendsError = true)
                        }
                        if (attempt < 4) delay(3000L * (attempt + 1))
                    }
                }
            }
        }
    }

    /** Chamado ao abrir o seletor: se ainda nao temos a lista e nada esta em curso, tenta. */
    fun ensureFriends() {
        if (!_state.value.friendsLoaded && friendsJob?.isActive != true) refreshFriends()
    }

    /** Botao "tentar de novo": recomeca ja, mesmo se um retry estiver dormindo. */
    fun retryFriends() {
        _state.value = _state.value.copy(friendsError = false)
        refreshFriends()
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val sharer = repo.grants("sharer")
            val recipient = repo.grants("recipient")
            val pending = repo.pendingGrants()
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                sharing = (sharer as? ApiResult.Ok)?.value.orEmpty(),
                receiving = (recipient as? ApiResult.Ok)?.value.orEmpty(),
                incoming = (pending as? ApiResult.Ok)?.value?.incoming.orEmpty(),
                outgoing = (pending as? ApiResult.Ok)?.value?.outgoing.orEmpty(),
                error = (sharer as? ApiResult.Failure)?.message,
            )
        }
        refreshFriends()
    }

    fun offerTo(nickname: String) =
        grantAction("offer:$nickname", "Oferta enviada para @$nickname.") { repo.offerGrant(nickname) }

    fun requestFrom(nickname: String) =
        grantAction("request:$nickname", "Pedido enviado para @$nickname.") { repo.requestGrant(nickname) }

    private fun grantAction(key: String, okMsg: String, call: suspend () -> ApiResult<GrantDto>) = launchBusy(key) {
        val r = call()
        val activeNow = r is ApiResult.Ok && r.value.status == "active"
        _state.value = _state.value.copy(
            notice = when {
                activeNow -> "Compartilhamento ativado."
                r is ApiResult.Ok -> okMsg
                r is ApiResult.Failure -> r.message
                else -> null
            },
            noticeActionNick = if (activeNow) (r as ApiResult.Ok).value.counterpart else null,
        )
        load(silent = true)
    }

    fun dismissNotice() { _state.value = _state.value.copy(notice = null, noticeActionNick = null) }

    fun togglePause(g: GrantDto) = launchBusy("pause:${g.id}") {
        if (g.isPausedByMe) repo.resumeGrant(g.id) else repo.pauseGrant(g.id)
        load(silent = true)
    }

    fun accept(id: String) = launchBusy("accept:$id") {
        val r = repo.acceptGrant(id)
        if (r is ApiResult.Ok) {
            // atalho de onboarding: leva pras notificacoes desse contato
            _state.value = _state.value.copy(
                notice = "Compartilhamento com @${r.value.counterpart} ativado.",
                noticeActionNick = r.value.counterpart,
            )
        }
        load(silent = true)
    }
    fun decline(id: String) = launchBusy("decline:$id") { repo.declineGrant(id); load(silent = true) }
    fun revoke(id: String) = launchBusy("revoke:$id") { repo.revokeGrant(id); load(silent = true) }

    private fun launchBusy(key: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = it.busy + key) }
        try {
            block()
        } finally {
            _state.update { it.copy(busy = it.busy - key) }
            // qualquer acao de grant: outras telas (Amigos, hub, Pedidos) releem
            com.notifyshare.core.AppEvents.signal(com.notifyshare.core.AppEvents.GRANTS)
        }
    }
}

@Composable
fun ShareScreen(
    vm: ShareViewModel,
    onOpenIncomingRequests: () -> Unit,
    onOpenOutgoingRequests: () -> Unit,
    onOpenRules: (grantId: String, nickname: String) -> Unit,
    onOpenNotifyRules: (grantId: String, nickname: String) -> Unit,
    onOpenNotifications: (nickname: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Compartilhamentos", trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Recebidos: precisam de uma acao (aceitar/recusar), por isso o
                // estilo marcante — nao faz sentido deixar isso "parado" ali.
                if (state.incoming.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenIncomingRequests)
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            NotifyIcons.Bell, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "${state.incoming.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                // Enviados: so aguardando resposta de outra pessoa, sem acao
                // pendente do usuario — estilo discreto, de proposito.
                if (state.outgoing.isNotEmpty()) {
                    Text(
                        "${state.outgoing.size} enviados",
                        style = MaterialTheme.typography.labelMedium,
                        color = NotifyShareColors.muted,
                        modifier = Modifier
                            .clickable(onClick = onOpenOutgoingRequests)
                            .padding(12.dp),
                    )
                }
                Icon(
                    NotifyIcons.Plus, "Novo compartilhamento",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { picker = true; vm.ensureFriends() }.padding(12.dp),
                )
            }
        })

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Compartilho com") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Recebo de") })
        }

        state.notice?.let { msg ->
            NoticeBar(msg, state.noticeActionNick, onOpenNotifications, vm::dismissNotice)
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
                                    toggling = "pause:${g.id}" in state.busy,
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
            available = state.friends.filterNot { it in already },
            hasAnyFriend = state.friends.isNotEmpty(),
            friendsLoaded = state.friendsLoaded,
            friendsError = state.friendsError && !state.friendsLoaded,
            onRetry = vm::retryFriends,
            onDismiss = { picker = false },
            onPick = { nick ->
                picker = false
                if (tab == 1) vm.requestFrom(nick) else vm.offerTo(nick)
            },
        )
    }
}

/** Aviso curto acima da lista. Com [actionNick], ganha o atalho "Ver notificações de @X". */
@Composable
private fun NoticeBar(
    msg: String,
    actionNick: String?,
    onOpenNotifications: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                "OK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        if (actionNick != null) {
            Text(
                "Ver notificações de @$actionNick  →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onDismiss(); onOpenNotifications(actionNick) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NewShareDialog(
    request: Boolean,
    available: List<String>,
    hasAnyFriend: Boolean,
    friendsLoaded: Boolean,
    friendsError: Boolean,
    onRetry: () -> Unit,
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
            when {
                available.isNotEmpty() -> LazyColumn {
                    items(available, key = { it }) { nick ->
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

                friendsError -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Não foi possível carregar seus amigos agora.")
                    TextButton(onClick = onRetry) { Text("Tentar de novo") }
                }

                !friendsLoaded -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Carregando amigos…")
                }

                hasAnyFriend -> Text(
                    if (request) "Você já pede notificações de todos os seus amigos."
                    else "Você já compartilha com todos os seus amigos.",
                )

                else -> Text("Você ainda não tem amigos. Adicione alguém na aba Amigos primeiro.")
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun GrantRow(
    g: GrantDto,
    isSharer: Boolean,
    toggling: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
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
            if (toggling) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Switch(checked = g.isActive, onCheckedChange = { onToggle() })
            }
        }
    }
}

// --- Pedidos (caixa de pedidos de grant) -------------------------------------
//
// Recebidos e enviados viraram DUAS telas, nao uma so com duas secoes: quem
// recebeu precisa agir (aceitar/recusar), quem enviou so esta esperando — sao
// urgencias diferentes, e o estilo de cada tela reflete isso.

/** Pedidos que outras pessoas te mandaram — estilo de notificacao, de proposito. */
@Composable
fun IncomingRequestsScreen(
    vm: ShareViewModel,
    onBack: () -> Unit,
    onOpenNotifications: (nickname: String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Pedidos recebidos", onBack = onBack)

        state.notice?.let { msg ->
            NoticeBar(msg, state.noticeActionNick, onOpenNotifications, vm::dismissNotice)
        }

        com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh) {
            when {
                // "nenhum pedido" so depois que a primeira carga terminou. Antes
                // disso, spinner — nunca uma informacao falsa de que nao ha nada.
                state.incoming.isEmpty() && state.loading -> LoadingBox()
                state.incoming.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item { EmptyState("Nenhum pedido recebido.") }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.incoming, key = { it.id }) { g ->
                        PendingCard(
                            title = if (g.role == "sharer") "@${g.counterpart} quer receber suas notificações"
                            else "@${g.counterpart} ofereceu compartilhar com você",
                            accepting = "accept:${g.id}" in state.busy,
                            declining = "decline:${g.id}" in state.busy,
                            onAccept = { vm.accept(g.id) },
                            onDecline = { vm.decline(g.id) },
                            prominent = true,
                        )
                    }
                }
            }
        }
    }
}

/** Pedidos que voce mandou — so aguardando resposta, sem acao pendente sua. */
@Composable
fun OutgoingRequestsScreen(
    vm: ShareViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Pedidos enviados", onBack = onBack)

        com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh) {
            when {
                state.outgoing.isEmpty() && state.loading -> LoadingBox()
                state.outgoing.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item { EmptyState("Nenhum pedido enviado.") }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.outgoing, key = { it.id }) { g ->
                        PendingCard(
                            title = if (g.role == "sharer") "Você ofereceu compartilhar com @${g.counterpart}"
                            else "Você pediu para receber de @${g.counterpart}",
                            accepting = false,
                            declining = "decline:${g.id}" in state.busy,
                            onAccept = null,
                            onDecline = { vm.decline(g.id) },
                            prominent = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCard(
    title: String,
    accepting: Boolean,
    declining: Boolean,
    onAccept: (() -> Unit)?,
    onDecline: () -> Unit,
    prominent: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                if (prominent) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (prominent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.notifyshare.ui.common.InlineActionButton(
                if (onAccept == null) "Cancelar" else "Recusar",
                onDecline,
                loading = declining,
            )
            if (onAccept != null) {
                com.notifyshare.ui.common.PillButton(
                    "Aceitar",
                    onAccept,
                    loading = accepting,
                    enabled = !declining,
                )
            }
        }
    }
}
