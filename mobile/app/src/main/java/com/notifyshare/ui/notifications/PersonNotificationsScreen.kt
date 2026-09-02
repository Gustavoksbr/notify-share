package com.notifyshare.ui.notifications

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
import com.notifyshare.ui.common.InfiniteListHandler
import com.notifyshare.ui.common.LoadMoreFooter
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.NotificationFiltersSheet
import com.notifyshare.ui.common.PullRefresh
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.format.dayBucket
import com.notifyshare.ui.format.eventBody
import com.notifyshare.ui.format.eventTitle
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyShareColors
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
    val page: Int = 0,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
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
        load(_state.value.direction, silent = true)
    }

    fun applyFilter(filter: FeedQuery) {
        _state.value = _state.value.copy(filter = filter)
        load(_state.value.direction, silent = true)
    }

    fun clearFilter() = applyFilter(FeedQuery())

    fun load(direction: String, silent: Boolean = false) {
        _state.value = _state.value.copy(loading = !silent, direction = direction)
        val current = _state.value
        viewModelScope.launch {
            when (val r = repo.conversation(nickname, direction, current.filter, page = 0)) {
                is ApiResult.Ok -> _state.value = current.copy(
                    loading = false,
                    refreshing = false,
                    items = r.value,
                    failedToLoad = false,
                    page = 0,
                    endReached = r.value.size < repo.pageSize,
                    loadingMore = false,
                    knownPackages = (current.knownPackages + r.value.map { it.packageName }).distinct().sorted(),
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

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.endReached || current.loading) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val next = current.page + 1
            when (val r = repo.conversation(nickname, current.direction, current.filter, page = next)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    items = (current.items + r.value).distinctBy { it.deliveryId },
                    page = next,
                    loadingMore = false,
                    endReached = r.value.size < repo.pageSize,
                    knownPackages = (_state.value.knownPackages + r.value.map { it.packageName }).distinct().sorted(),
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }
}

@Composable
fun PersonNotificationsScreen(vm: PersonNotificationsViewModel, nickname: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("@$nickname", onBack = onBack, trailing = {
            val n = state.activeFilterCount
            Text(
                if (n > 0) "Filtros · $n" else "Filtros",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showFilters = true }.padding(12.dp),
            )
        })

        TabRow(
            selectedTabIndex = if (state.direction == "received") 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = state.direction == "received",
                onClick = { vm.load("received") },
                text = { Text("Ela envia") },
            )
            Tab(
                selected = state.direction == "sent",
                onClick = { vm.load("sent") },
                text = { Text("Eu envio") },
            )
        }

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
                    val listState = rememberLazyListState()
                    InfiniteListHandler(listState, onLoadMore = vm::loadMore)
                    val grouped = state.items.groupBy { dayBucket(it.occurredAt) }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (bucket, rows) ->
                            item(key = "h_$bucket") { SectionLabel(bucket) }
                            items(rows, key = { it.deliveryId }) { row -> Item(row) }
                        }
                        if (state.loadingMore) item(key = "load_more") { LoadMoreFooter() }
                    }
                }
            }
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
}

@Composable
private fun Item(row: FeedItemDto) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(13.dp),
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
            Text(
                eventBody(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
