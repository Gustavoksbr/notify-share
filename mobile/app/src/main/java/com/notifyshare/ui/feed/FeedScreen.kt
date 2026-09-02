package com.notifyshare.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.notifyshare.core.AppEvents
import com.notifyshare.data.ApiResult
import com.notifyshare.data.FeedRepository
import com.notifyshare.data.remote.FeedItemDto
import com.notifyshare.session.SessionState
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.ErrorRetry
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.common.StatusBanner
import com.notifyshare.ui.format.dayBucket
import com.notifyshare.ui.format.eventBody
import com.notifyshare.ui.format.eventTitle
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val loading: Boolean = true,
    val items: List<FeedItemDto> = emptyList(),
    val activeShares: Int = 0,
    val error: String? = null,
    val refreshing: Boolean = false,
    val filter: com.notifyshare.data.FeedQuery = com.notifyshare.data.FeedQuery(),
    /** pessoas e apps ja vistos no feed — alimentam o painel de filtros */
    val knownPeople: List<String> = emptyList(),
    val knownPackages: List<String> = emptyList(),
    /** paginacao: 100 por pagina */
    val page: Int = 0,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
) {
    val activeFilterCount: Int
        get() = listOf(
            filter.from, filter.packageName, filter.type,
            filter.period.takeIf { it != "all" },
        ).count { it != null }
}

class FeedViewModel(
    private val repo: FeedRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            AppEvents.bus.collect { if (it == AppEvents.FEED) load(silent = true) }
        }
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load(silent = true)
    }

    fun applyFilter(filter: com.notifyshare.data.FeedQuery) {
        _state.value = _state.value.copy(filter = filter, loading = true, error = null)
        load(silent = true)
    }

    fun clearFilter() = applyFilter(com.notifyshare.data.FeedQuery())

    fun load(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true, error = null)
        val current = _state.value
        viewModelScope.launch {
            when (val r = repo.feed(current.filter, page = 0)) {
                is ApiResult.Ok -> _state.value = current.copy(
                    loading = false,
                    refreshing = false,
                    error = null,
                    items = r.value,
                    page = 0,
                    endReached = r.value.size < repo.pageSize,
                    loadingMore = false,
                    knownPeople = (current.knownPeople + r.value.map { it.from }).distinct().sorted(),
                    knownPackages = (current.knownPackages + r.value.map { it.packageName }).distinct().sorted(),
                )
                is ApiResult.Failure -> _state.value =
                    current.copy(loading = false, refreshing = false, error = r.message)
            }
        }
    }

    /** Proxima pagina (scroll infinito). */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.endReached || current.loading) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val next = current.page + 1
            when (val r = repo.feed(current.filter, page = next)) {
                is ApiResult.Ok -> {
                    val merged = (current.items + r.value).distinctBy { it.deliveryId }
                    _state.value = _state.value.copy(
                        items = merged,
                        page = next,
                        loadingMore = false,
                        endReached = r.value.size < repo.pageSize,
                        knownPeople = (_state.value.knownPeople + r.value.map { it.from }).distinct().sorted(),
                        knownPackages = (_state.value.knownPackages + r.value.map { it.packageName }).distinct().sorted(),
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    fun markRead(deliveryId: String) {
        viewModelScope.launch {
            repo.markRead(deliveryId)
            _state.value = _state.value.copy(
                items = _state.value.items.map {
                    if (it.deliveryId == deliveryId) it.copy(read = true) else it
                },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: FeedViewModel, onOpenPerson: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Notify Share", trailing = {
            val n = state.activeFilterCount
            Text(
                if (n > 0) "Filtros · $n" else "Filtros",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showFilters = true }
                    .padding(12.dp),
            )
        })

        when {
            state.loading -> LoadingBox()
            state.error != null && state.items.isEmpty() -> ErrorRetry(state.error!!, vm::load)
            else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isEmpty()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            EmptyState(
                                if (state.activeFilterCount > 0)
                                    "Nada bate com esses filtros."
                                else
                                    "Nada por aqui ainda.\nQuando alguem compartilhar notificacoes com voce, elas aparecem aqui.",
                            )
                        }
                    }
                } else {
                    FeedList(
                        state.items, state.loadingMore, vm::loadMore,
                        onOpenPerson, vm::markRead,
                    )
                }
            }
        }
    }

    if (showFilters) {
        com.notifyshare.ui.common.NotificationFiltersSheet(
            current = state.filter,
            people = state.knownPeople,
            packages = state.knownPackages,
            onDismiss = { showFilters = false },
            onApply = { vm.applyFilter(it); showFilters = false },
            onClear = { vm.clearFilter(); showFilters = false },
        )
    }
}

@Composable
private fun FeedList(
    items: List<FeedItemDto>,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onMarkRead: (String) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.notifyshare.ui.common.InfiniteListHandler(listState, onLoadMore = onLoadMore)

    val grouped = items.groupBy { dayBucket(it.occurredAt) }
    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (bucket, rows) ->
            item(key = "h_$bucket") { SectionLabel(bucket) }
            items(rows, key = { it.deliveryId }) { row ->
                FeedCard(row, onClick = {
                    if (!row.read) onMarkRead(row.deliveryId)
                    onOpenPerson(row.from)
                })
            }
        }
        if (loadingMore) item(key = "load_more") { com.notifyshare.ui.common.LoadMoreFooter() }
    }
}

@Composable
private fun FeedCard(row: FeedItemDto, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                if (row.read) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        com.notifyshare.ui.common.AppIcon(row.packageName, size = 34.dp, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    eventTitle(row),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    shortTime(row.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = NotifyShareColors.muted,
                )
            }
            Text(
                eventBody(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "via @${row.from}",
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!row.read) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
