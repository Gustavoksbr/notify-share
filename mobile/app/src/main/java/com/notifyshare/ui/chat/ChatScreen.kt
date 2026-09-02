package com.notifyshare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import com.notifyshare.ui.common.prettyPackage
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
    /** Mensagem a que estou respondendo (compositor mostra o trecho). */
    val replyingTo: TimelineItemDto? = null,
    /** Mensagem que estou editando (compositor entra em modo edicao). */
    val editing: TimelineItemDto? = null,
    /** Notificacao a vincular na proxima mensagem (veio de "Responder" no feed). */
    val linkedEventId: String? = null,
    val linkedEventLabel: String? = null,
)

class ChatViewModel(
    private val repo: ChatRepository,
    @Suppress("unused") private val realtime: RealtimeClient,
    private val nickname: String,
    linkedEventId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(linkedEventId = linkedEventId?.takeIf { it.isNotBlank() }),
    )
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
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(
                        loading = false, refreshing = false, items = r.value, failedToLoad = false,
                    )
                    resolveLinkedLabel(r.value)
                }
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        failedToLoad = _state.value.items.isEmpty(),
                    )
            }
        }
    }

    private fun resolveLinkedLabel(items: List<TimelineItemDto>) {
        val id = _state.value.linkedEventId ?: return
        val found = items.firstNotNullOfOrNull { it.linkedEvent?.takeIf { e -> e.eventId == id } }
        if (found != null) {
            _state.value = _state.value.copy(linkedEventLabel = prettyPackage(found.packageName))
        }
    }

    private fun refreshPresence() = viewModelScope.launch {
        (repo.presence(listOf(nickname)) as? ApiResult.Ok)?.value?.firstOrNull()?.let { p ->
            _state.value = _state.value.copy(online = p.online, lastSeen = p.lastSeen)
        }
    }

    fun onInput(v: String) { _state.value = _state.value.copy(input = v) }

    fun startReply(item: TimelineItemDto) {
        _state.value = _state.value.copy(replyingTo = item, editing = null)
    }

    fun startEdit(item: TimelineItemDto) {
        _state.value = _state.value.copy(editing = item, replyingTo = null, input = item.body.orEmpty())
    }

    fun cancelCompose() {
        _state.value = _state.value.copy(
            replyingTo = null,
            editing = null,
            input = if (_state.value.editing != null) "" else _state.value.input,
        )
    }

    fun clearLinkedEvent() {
        _state.value = _state.value.copy(linkedEventId = null, linkedEventLabel = null)
    }

    fun deleteMessage(item: TimelineItemDto) = viewModelScope.launch {
        repo.delete(item.id)
        load(silent = true)
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        val editing = _state.value.editing
        val replyId = _state.value.replyingTo?.id
        val eventId = _state.value.linkedEventId
        _state.value = _state.value.copy(sending = true, input = "")
        viewModelScope.launch {
            val r: ApiResult<*> = if (editing != null) {
                repo.edit(editing.id, text)
            } else {
                repo.send(nickname, text, replyToId = replyId, linkedEventId = eventId)
            }
            when (r) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(
                        sending = false, blockedNotice = null,
                        replyingTo = null, editing = null,
                        linkedEventId = null, linkedEventLabel = null,
                    )
                    load(silent = true)
                }
                is ApiResult.Failure -> {
                    val blocked = r.code == "blocked_by_them" || r.code == "you_blocked_them"
                    _state.value = _state.value.copy(
                        sending = false,
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
                        else -> MessageBubble(
                            item = item,
                            onReply = { vm.startReply(item) },
                            onEdit = { vm.startEdit(item) },
                            onDelete = { vm.deleteMessage(item) },
                            onOpenLinked = onOpenNotifications,
                        )
                    }
                }
            }
        }

        if (state.blockedNotice != null) {
            Text(
                state.blockedNotice!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            )
        }

        ComposeContext(state, vm::cancelCompose, vm::clearLinkedEvent)

        val canSend = state.blockedNotice == null
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInput,
                placeholder = {
                    Text(
                        when {
                            !canSend -> "Não é possível enviar mensagens"
                            state.editing != null -> "Editar mensagem"
                            else -> "Mensagem"
                        },
                    )
                },
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
                Icon(
                    if (state.editing != null) NotifyIcons.Check else NotifyIcons.Send,
                    "Enviar",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Faixa acima do compositor: respondendo a…, editando…, ou sobre a notificação…. */
@Composable
private fun ComposeContext(
    state: ChatUiState,
    onCancel: () -> Unit,
    onClearLinked: () -> Unit,
) {
    val reply = state.replyingTo
    val editing = state.editing

    when {
        editing != null -> ContextBar("Editando mensagem", editing.body.orEmpty(), onCancel)
        reply != null -> ContextBar(
            if (reply.mine) "Respondendo a você" else "Respondendo",
            if (reply.deleted) "mensagem apagada" else reply.body.orEmpty(),
            onCancel,
        )
        state.linkedEventId != null -> ContextBar(
            "Sobre a notificação",
            state.linkedEventLabel ?: "notificação selecionada",
            onClearLinked,
        )
    }
}

@Composable
private fun ContextBar(title: String, preview: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .padding(end = 10.dp)
                .size(width = 3.dp, height = 30.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = NotifyShareColors.muted,
                maxLines = 1,
            )
        }
        Icon(
            NotifyIcons.Close, "Cancelar",
            tint = NotifyShareColors.muted,
            modifier = Modifier.clickable(onClick = onClose).padding(10.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    item: TimelineItemDto,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLinked: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val mineColors = item.mine

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        if (mineColors) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(16.dp),
                    )
                    .combinedClickable(
                        onClick = { if (item.linkedEvent != null) onOpenLinked() },
                        onLongClick = { if (!item.deleted) menu = true },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                item.replyTo?.let { r ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            if (r.deleted) "mensagem apagada"
                            else "${if (r.mine) "Você" else "@…"}: ${r.preview}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NotifyShareColors.muted,
                            maxLines = 2,
                        )
                    }
                }

                item.linkedEvent?.let { e ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        com.notifyshare.ui.common.AppIcon(e.packageName, size = 18.dp)
                        Text(
                            "sobre ${prettyPackage(e.packageName)} · ${shortTime(e.occurredAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (item.deleted) {
                    Text(
                        "mensagem apagada",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = NotifyShareColors.muted,
                    )
                } else {
                    Text(
                        item.body.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mineColors) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    buildString {
                        append(shortTime(item.at))
                        if (item.edited && !item.deleted) append(" · editado")
                        if (item.mine && item.readAt != null) append(" · lido")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NotifyShareColors.muted,
                )
            }

            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Responder") },
                    onClick = { menu = false; onReply() },
                )
                if (item.mine) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
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
