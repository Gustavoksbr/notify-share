package com.notifyshare.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.FeedQuery
import com.notifyshare.data.FeedRepository
import com.notifyshare.data.remote.FeedItemDto
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.NotificationFiltersSheet
import com.notifyshare.ui.common.PillButton
import com.notifyshare.ui.common.PullRefresh
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.format.dayBucket
import com.notifyshare.ui.format.eventBody
import com.notifyshare.ui.format.eventGroup
import com.notifyshare.ui.format.eventSender
import com.notifyshare.ui.format.eventTitle
import com.notifyshare.ui.format.friendlyPackage
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonNotifState(
    val loading: Boolean = true,
    val direction: String = "received",
    val items: List<FeedItemDto> = emptyList(),
    val failedToLoad: Boolean = false,
    val refreshing: Boolean = false,
    val filter: FeedQuery = FeedQuery(),
    val knownPackages: List<String> = emptyList(),
    /** paginacao por numero de pagina, igual a de Salvos (pagina atual, base 0) */
    val page: Int = 0,
    val totalCount: Long = 0,
) {
    val activeFilterCount: Int
        get() = listOf(filter.packageName, filter.type, filter.period.takeIf { it != "all" }).count { it != null }
}

class PersonNotificationsViewModel(
    private val repo: FeedRepository,
    private val nickname: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonNotifState())
    val state: StateFlow<PersonNotifState> = _state.asStateFlow()

    init { load("received") }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(_state.value.direction, silent = true, page = _state.value.page)
    }

    fun applyFilter(filter: FeedQuery) {
        _state.value = _state.value.copy(filter = filter)
        load(_state.value.direction, silent = true, page = 0)
    }

    fun clearFilter() = applyFilter(FeedQuery())

    /** Troca de pagina (paginacao numerada, igual a de Salvos). */
    fun goToPage(page: Int) = load(_state.value.direction, silent = true, page = page)

    fun load(direction: String, silent: Boolean = false, page: Int = 0) {
        _state.value = _state.value.copy(loading = !silent, direction = direction)
        val current = _state.value
        viewModelScope.launch {
            when (val r = repo.conversation(nickname, direction, current.filter, page = page)) {
                is ApiResult.Ok -> _state.value = current.copy(
                    loading = false,
                    refreshing = false,
                    items = r.value.items,
                    failedToLoad = false,
                    page = page,
                    totalCount = r.value.total,
                    knownPackages = (current.knownPackages + r.value.items.map { it.packageName }).distinct().sorted(),
                )
                is ApiResult.Failure -> _state.value = current.copy(
                    loading = false,
                    refreshing = false,
                    items = emptyList(),
                    failedToLoad = true,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersonNotificationsScreen(
    vm: PersonNotificationsViewModel,
    nickname: String,
    onBack: () -> Unit,
    highlightEventId: String? = null,
    /** Carrega a notificação inteira (não só o id) — quem chama já tem o texto
     *  pra mostrar no compositor sem esperar o envio ida-e-volta. */
    onReplyToNotification: (row: FeedItemDto) -> Unit = {},
    /** Dentro do hub da pessoa o cabecalho ja existe — aqui vira so uma barra de filtro. */
    embedded: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var detailOf by remember { mutableStateOf<FeedItemDto?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Chegou aqui vindo de "responder uma notificação" no chat: rola ate a
    // notificação em questao e pisca o fundo. `pending` guarda o alvo só até
    // achar e destacar — sem isso, trocar de aba NA MÃO depois (Ele
    // envia/Eu envio) mudava state.items de novo, o efeito disparava de novo
    // achando que ainda precisava trocar de aba, e voltava sozinho pra aba
    // onde o evento estava (o bug do "some sozinho de volta").
    var pending by remember(highlightEventId) { mutableStateOf(highlightEventId) }
    androidx.compose.runtime.LaunchedEffect(pending, state.items) {
        val target = pending ?: return@LaunchedEffect

        // Se não encontrou o evento na aba atual, tenta a outra aba
        if (state.items.none { it.eventId == target }) {
            if (!state.loading) {
                val newDirection = if (state.direction == "received") "sent" else "received"
                vm.load(newDirection, silent = false)
            }
            return@LaunchedEffect
        }

        val flat = flatIndexOfEvent(state.items, target)
        if (flat >= 0) {
            listState.animateScrollToItem(flat)
            highlightId = state.items.firstOrNull { it.eventId == target }?.deliveryId
            pending = null // achado e destacado — não reage mais a troca manual de aba
            delay(2000)
            highlightId = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (embedded) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val n = state.activeFilterCount
                Text(
                    if (n > 0) "Filtros · $n" else "Filtros",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showFilters = true }.padding(12.dp),
                )
            }
        } else {
            ScreenTitle("@$nickname", onBack = onBack, trailing = {
                val n = state.activeFilterCount
                Text(
                    if (n > 0) "Filtros · $n" else "Filtros",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showFilters = true }.padding(12.dp),
                )
            })
        }

        TabRow(
            selectedTabIndex = if (state.direction == "received") 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = state.direction == "received",
                onClick = { vm.load("received") },
                text = { Text("Ele envia") },
            )
            Tab(
                selected = state.direction == "sent",
                onClick = { vm.load("sent") },
                text = { Text("Eu envio") },
            )
        }

        Column(Modifier.weight(1f)) {
            Column(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingBox()
                    state.failedToLoad && state.items.isEmpty() ->
                        com.notifyshare.ui.common.ErrorRetry(
                            "Não foi possível carregar estas notificações.",
                            { vm.load(state.direction) },
                        )
                    else -> PullRefresh(state.refreshing, vm::refresh) {
                        if (state.items.isEmpty()) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    EmptyState(
                                        if (state.activeFilterCount > 0) "Nada bate com esses filtros."
                                        else "Nada aqui ainda.",
                                    )
                                }
                            }
                        } else {
                            val grouped = state.items.groupBy { dayBucket(it.occurredAt) }
                            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                grouped.forEach { (bucket, rows) ->
                                    item(key = "h_$bucket") { SectionLabel(bucket) }
                                    items(rows, key = { it.deliveryId }) { row ->
                                        Item(
                                            row,
                                            highlighted = row.deliveryId == highlightId,
                                            onClick = { detailOf = row },
                                            onReply = { onReplyToNotification(row) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val pageSize = 100
            val totalPages = ((state.totalCount + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
            com.notifyshare.ui.common.PageBar(
                page = state.page + 1,
                totalPages = totalPages,
                totalCount = state.totalCount.toInt(),
                show = !state.loading && (state.items.isNotEmpty() || state.page > 0),
                onPage = { vm.goToPage(it - 1) },
            )
        }
    }

    if (showFilters) {
        NotificationFiltersSheet(
            current = state.filter,
            people = emptyList(),
            packages = state.knownPackages,
            onDismiss = { showFilters = false },
            onApply = { vm.applyFilter(it); showFilters = false },
            onClear = { vm.clearFilter(); showFilters = false },
        )
    }

    detailOf?.let { row ->
        NotificationDetailSheet(
            row = row,
            nickname = nickname,
            onReply = { onReplyToNotification(row); detailOf = null },
            onDismiss = { detailOf = null },
        )
    }
}

/** Indice achatado (headers + linhas) do evento na LazyColumn agrupada por dia. */
private fun flatIndexOfEvent(items: List<FeedItemDto>, eventId: String): Int {
    var idx = 0
    items.groupBy { dayBucket(it.occurredAt) }.forEach { (_, rows) ->
        idx++ // header do dia
        for (r in rows) {
            if (r.eventId == eventId) return idx
            idx++
        }
    }
    return -1
}

@Composable
private fun Item(row: FeedItemDto, highlighted: Boolean, onClick: () -> Unit, onReply: (() -> Unit)? = null) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        label = "notifHighlight",
    )
    val group = eventGroup(row)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .padding(13.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.notifyshare.ui.common.AppIcon(row.packageName, size = 32.dp, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        eventTitle(row),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        shortTime(row.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = NotifyShareColors.muted,
                    )
                }
                if (group != null) {
                    Text(
                        "no grupo $group",
                        style = MaterialTheme.typography.labelSmall,
                        color = NotifyShareColors.muted,
                    )
                }
                Text(
                    eventBody(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!row.notify) {
                    Text(
                        "só no feed — sem aviso",
                        style = MaterialTheme.typography.labelSmall,
                        color = NotifyShareColors.muted,
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
            ) {
                androidx.compose.material3.Icon(
                    com.notifyshare.ui.theme.NotifyIcons.MoreVertical,
                    contentDescription = null,
                    tint = NotifyShareColors.muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("mais detalhes", style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.muted)
            }
            if (onReply != null) {
                androidx.compose.material3.TextButton(
                    onClick = onReply,
                    modifier = Modifier.padding(0.dp),
                ) {
                    androidx.compose.material3.Icon(
                        com.notifyshare.ui.theme.NotifyIcons.Send,
                        contentDescription = "Mencionar",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Mencionar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDetailSheet(
    row: FeedItemDto,
    nickname: String,
    onReply: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                com.notifyshare.ui.common.AppIcon(row.packageName, size = 40.dp)
                Column {
                    Text(eventTitle(row), style = MaterialTheme.typography.titleMedium)
                    Text(
                        com.notifyshare.ui.common.appLabel(row.packageName),
                        style = MaterialTheme.typography.labelMedium,
                        color = NotifyShareColors.muted,
                    )
                }
            }

            DetailLine("De", "@$nickname")
            eventGroup(row)?.let { DetailLine("Grupo", it) }
            eventSender(row)?.let { DetailLine("Remetente", it) }
            DetailLine("Quando", com.notifyshare.ui.format.fullDateTime(row.occurredAt))
            DetailLine(
                "Como você recebe",
                if (row.notify) "Notifica você + entra no feed" else "Só no feed — sem aviso",
            )
            DetailLine(
                "Conteúdo compartilhado",
                if (row.mode == "sender_only") "Só o remetente (sem texto)" else eventBody(row),
            )
            DetailLine("Lida", if (row.read) "Sim" else "Não")

            if (onReply != null) {
                Spacer(Modifier.height(16.dp))
                com.notifyshare.ui.common.PillButton("Mencionar na conversa", onReply)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = NotifyShareColors.muted,
            modifier = Modifier.width(150.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
