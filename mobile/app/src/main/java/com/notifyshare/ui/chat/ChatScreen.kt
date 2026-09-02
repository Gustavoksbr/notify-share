package com.notifyshare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.ChatRepository
import com.notifyshare.data.remote.RealtimeClient
import com.notifyshare.data.remote.TimelineItemDto
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.StatusDot
import com.notifyshare.ui.format.relativeShort
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val items: List<TimelineItemDto> = emptyList(),
    val online: Boolean = false,
    val lastSeen: String? = null,
    val input: String = "",
    val sending: Boolean = false,
    val failedToLoad: Boolean = false,
    val refreshing: Boolean = false,
    /** Preenchido quando o envio falha por bloqueio (nos dois sentidos). */
    val blockedNotice: String? = null,
)

class ChatViewModel(
    private val repo: ChatRepository,
    @Suppress("unused") private val realtime: RealtimeClient,
    private val nickname: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        load()
        markRead()
        refreshPresence()
        viewModelScope.launch {
            AppEvents.bus.collect {
                when (it) {
                    AppEvents.CHAT -> { load(silent = true); markRead() }
                    AppEvents.PRESENCE -> refreshPresence()
                }
            }
        }
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            when (val r = repo.timeline(nickname)) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false, items = r.value, failedToLoad = false,
                    )
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        failedToLoad = _state.value.items.isEmpty(),
                    )
            }
        }
    }

    private fun refreshPresence() = viewModelScope.launch {
        (repo.presence(listOf(nickname)) as? ApiResult.Ok)?.value?.firstOrNull()?.let { p ->
            _state.value = _state.value.copy(online = p.online, lastSeen = p.lastSeen)
        }
    }

    fun onInput(v: String) { _state.value = _state.value.copy(input = v) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        _state.value = _state.value.copy(sending = true, input = "")
        viewModelScope.launch {
            when (val r = repo.send(nickname, text)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(sending = false, blockedNotice = null)
                    load(silent = true)
                }
                is ApiResult.Failure -> {
                    val blocked = r.code == "blocked_by_them" || r.code == "you_blocked_them"
                    _state.value = _state.value.copy(
                        sending = false,
                        // devolve o texto para o campo se nao foi enviado
                        input = if (_state.value.input.isBlank()) text else _state.value.input,
                        blockedNotice = if (blocked) r.message else _state.value.blockedNotice,
                    )
                }
            }
        }
    }

    fun markRead() = viewModelScope.launch { repo.markRead(nickname) }
}

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    nickname: String,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                NotifyIcons.Back, "Voltar",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(12.dp),
            )
            Avatar(
                nickname, size = 36, online = state.online,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Column(Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                Text("@$nickname", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    StatusDot(state.online)
                    Text(
                        if (state.online) "Online" else
                            listOf("Offline", relativeShort(state.lastSeen)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.online) NotifyShareColors.online else NotifyShareColors.muted,
                    )
                }
            }
            Text(
                "Notificações",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenNotifications).padding(8.dp),
            )
        }

        if (state.loading) {
            LoadingBox()
            return@Column
        }

        if (state.failedToLoad) {
            com.notifyshare.ui.common.ErrorRetry(
                "Não foi possível carregar a conversa.",
                { vm.load() },
                Modifier.weight(1f),
            )
            return@Column
        }

        com.notifyshare.ui.common.PullRefresh(
            state.refreshing, vm::refresh, Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    when (item.kind) {
                        "audit" -> AuditLine(item)
                        else -> MessageBubble(item)
                    }
                }
            }
        }

        if (state.blockedNotice != null) {
            Text(
                state.blockedNotice!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            )
        }

        val canSend = state.blockedNotice == null
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInput,
                placeholder = { Text(if (canSend) "Mensagem" else "Não é possível enviar mensagens") },
                shape = RoundedCornerShape(26.dp),
                enabled = canSend,
                modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    )
                    .clickable(enabled = !state.sending && canSend) { vm.send() },
            ) {
                Icon(NotifyIcons.Send, "Enviar", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun MessageBubble(item: TimelineItemDto) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (item.mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                item.body.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.mine) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                shortTime(item.at) + if (item.mine && item.readAt != null) " · lido" else "",
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
            )
        }
    }
}

@Composable
private fun AuditLine(item: TimelineItemDto) {
    val label = when (item.action) {
        "activated" -> "Compartilhamento ativado"
        "paused" -> "Compartilhamento pausado"
        "resumed" -> "Compartilhamento retomado"
        "revoked" -> "Compartilhamento encerrado"
        "rules_changed" -> "Regras atualizadas" + (item.body?.let { ": $it" } ?: "")
        "offered" -> "Ofereceu compartilhar"
        "requested" -> "Pediu para receber"
        else -> item.action.orEmpty()
    }
    Text(
        "$label · ${shortTime(item.at)}",
        style = MaterialTheme.typography.labelMedium,
        color = NotifyShareColors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(10.dp),
    )
}
