package com.notifyshare.ui.friends

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<SearchResultDto> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val incoming: List<FriendRequestDto> = emptyList(),
    val friends: List<FriendDto> = emptyList(),
    val error: String? = null,
    val failedToLoad: Boolean = false,
    val refreshing: Boolean = false,
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
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                friends = friendsOk?.value ?: _state.value.friends,
                incoming = (requests as? ApiResult.Ok)?.value?.incoming ?: _state.value.incoming,
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

    fun add(nickname: String) = viewModelScope.launch {
        repo.addFriend(nickname)
        onQuery(_state.value.query)
        load(silent = true)
    }

    fun accept(id: String) = viewModelScope.launch { repo.acceptFriend(id); load(silent = true) }
    fun decline(id: String) = viewModelScope.launch { repo.declineFriend(id); load(silent = true) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    vm: FriendsViewModel,
    onOpenRequests: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

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
                    SearchRow(r, onAdd = { vm.add(r.nickname) }, onOpen = { onOpenProfile(r.nickname) })
                }
            }

            if (state.incoming.isNotEmpty()) {
                item { SectionLabel("Pedidos de amizade · ${state.incoming.size}") }
                items(state.incoming, key = { "req_${it.id}" }) { req ->
                    RequestRow(req, onAccept = { vm.accept(req.id) }, onDecline = { vm.decline(req.id) })
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
                        onClick = { onOpenChat(f.nickname) },
                        onAvatar = { onOpenProfile(f.nickname) },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SearchRow(r: SearchResultDto, onAdd: () -> Unit, onOpen: () -> Unit) {
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
        if (r.relation == "none" || r.relation == "request_received") {
            PillButton(if (r.relation == "request_received") "Aceitar" else "Adicionar", onAdd)
        }
    }
}

@Composable
private fun RequestRow(req: FriendRequestDto, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Avatar(req.nickname, size = 40)
        Text("@${req.nickname}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onDecline) { Text("Recusar", color = NotifyShareColors.muted) }
        PillButton("Aceitar", onAccept)
    }
}

@Composable
private fun FriendRow(f: FriendDto, onClick: () -> Unit, onAvatar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Avatar(f.nickname, online = f.online, modifier = Modifier.clickable(onClick = onAvatar))
        Column(Modifier.weight(1f)) {
            Text("@${f.nickname}", style = MaterialTheme.typography.titleMedium)
            Text(
                if (f.online) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (f.online) NotifyShareColors.online else NotifyShareColors.muted,
            )
        }
        Icon(NotifyIcons.Chevron, null, tint = NotifyShareColors.muted)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
