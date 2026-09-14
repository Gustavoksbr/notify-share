package com.notifyshare.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.data.local.VaultItem
import com.notifyshare.ui.common.AppIcon
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.common.shareTextExport
import com.notifyshare.ui.format.dayBucket
import com.notifyshare.ui.format.friendlyPackage
import com.notifyshare.ui.format.fullDateTime
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * O "cofre": tudo que as regras locais capturaram, mais novo no topo. Vive só
 * neste aparelho. O quê guardar se configura na aba "Apps" (ver VaultAppsNav),
 * não daqui — timeline e configuração são coisas separadas.
 */
@Composable
fun VaultTimelineScreen(container: AppContainer) {
    val items by container.vault.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf<VaultItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var detailOf by remember { mutableStateOf<VaultItem?>(null) }
    var viewFilter by remember { mutableStateOf(VaultQuery()) }
    var showFilters by remember { mutableStateOf(false) }
    var showExportChoice by remember { mutableStateOf(false) }
    var showExportFilters by remember { mutableStateOf(false) }

    val packages = remember(items) { items.map { it.packageName }.distinct().sorted() }
    val senders = remember(items) { items.mapNotNull { it.sender }.distinct().sorted() }
    val filtered = remember(items, viewFilter) { items.filter { it.matches(viewFilter) } }

    // Quanto mais filtrado, menor a página: um filtro bem específico já devolve
    // poucos resultados, não faz sentido continuar paginando do tamanho cheio.
    // O tamanho-base vem de VAULT_PAGE_SIZE no .env (mobile/.env) — vazio = 100.
    val basePageSize = com.notifyshare.BuildConfig.VAULT_PAGE_SIZE.toIntOrNull()?.takeIf { it > 0 } ?: 100
    val pageSize = when (viewFilter.activeCount) {
        0 -> basePageSize
        1 -> (basePageSize / 2).coerceAtLeast(1)
        2 -> (basePageSize / 4).coerceAtLeast(1)
        else -> (basePageSize / 10).coerceAtLeast(1)
    }
    var page by remember(viewFilter) { mutableStateOf(1) }
    val totalPages = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    LaunchedEffect(totalPages) { if (page > totalPages) page = totalPages }
    val pageItems = remember(filtered, page, pageSize) {
        filtered.drop((page - 1) * pageSize).take(pageSize)
    }

    fun export(toExport: List<VaultItem>) {
        shareTextExport(context, "notify-share-cofre.json", buildVaultExportJson(toExport), mime = "application/json")
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Salvos",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty()) {
                        val n = viewFilter.activeCount
                        Text(
                            if (n > 0) "Filtros · $n" else "Filtros",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showFilters = true }.padding(8.dp),
                        )
                        Text(
                            "Exportar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showExportChoice = true }.padding(8.dp),
                        )
                    }
                }
            },
        )

        if (items.isEmpty()) {
            EmptyState(
                "Nada guardado ainda.\nNa aba \"Apps\", escolha o que este " +
                    "aparelho deve guardar — só aqui, nunca na nuvem.",
            )
            return
        }

        Box(Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                EmptyState("Nada bate com esses filtros.")
            } else {
                val grouped = pageItems.groupBy { dayBucket(it.occurredAt) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (bucket, rows) ->
                        item(key = "h_$bucket") { SectionLabel(bucket) }
                        items(rows, key = { it.id }) { item ->
                            VaultRow(item, onDelete = { confirmDelete = item }, onOpenDetail = { detailOf = item })
                        }
                    }
                }
            }
        }

        VaultBottomBar(
            page = page,
            totalPages = totalPages,
            totalCount = filtered.size,
            showPagination = filtered.isNotEmpty(),
            onPage = { page = it },
            onClearAll = { confirmClear = true },
        )
    }

    if (showFilters) {
        VaultFiltersSheet(
            current = viewFilter,
            packages = packages,
            senders = senders,
            onDismiss = { showFilters = false },
            onApply = { q -> viewFilter = q; showFilters = false },
            onClear = { viewFilter = VaultQuery(); showFilters = false },
        )
    }

    if (showExportChoice) {
        AlertDialog(
            onDismissRequest = { showExportChoice = false },
            title = { Text("Exportar") },
            text = { Text("Exportar tudo o que está guardado, ou só o que bate com um filtro?") },
            confirmButton = {
                TextButton(onClick = { showExportChoice = false; export(items) }) { Text("Tudo") }
            },
            dismissButton = {
                TextButton(onClick = { showExportChoice = false; showExportFilters = true }) {
                    Text("Com filtros…")
                }
            },
        )
    }

    if (showExportFilters) {
        VaultFiltersSheet(
            current = VaultQuery(),
            packages = packages,
            senders = senders,
            confirmLabel = "Exportar",
            onDismiss = { showExportFilters = false },
            onApply = { q -> showExportFilters = false; export(items.filter { it.matches(q) }) },
            onClear = { showExportFilters = false; export(items) },
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remover este item?") },
            text = { Text("Sai do cofre deste aparelho. Não afeta a notificação original.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.vault.deleteItem(item.id) }
                    confirmDelete = null
                }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }

    detailOf?.let { item ->
        VaultDetailSheet(item = item, onDismiss = { detailOf = null })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar o cofre?") },
            text = { Text("Apaga tudo do cofre. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.vault.clearItems() }
                    confirmClear = false
                }) { Text("Limpar tudo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Serializable
private data class VaultExport(
    val exportedAt: String,
    val count: Int,
    val items: List<VaultItem>,
)

private val exportJson = Json { prettyPrint = true }

private fun buildVaultExportJson(items: List<VaultItem>): String {
    val payload = VaultExport(
        exportedAt = java.time.Instant.now().toString(),
        count = items.size,
        items = items.sortedByDescending { it.occurredAt },
    )
    return exportJson.encodeToString(payload)
}

@Composable
private fun VaultBottomBar(
    page: Int,
    totalPages: Int,
    totalCount: Int,
    showPagination: Boolean,
    onPage: (Int) -> Unit,
    onClearAll: () -> Unit,
) {
    com.notifyshare.ui.common.PageBar(
        page = page,
        totalPages = totalPages,
        totalCount = totalCount,
        show = showPagination,
        onPage = onPage,
        footer = {
            Text(
                "Limpar tudo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearAll)
                    .padding(vertical = 14.dp),
            )
        },
    )
}

@Composable
private fun VaultRow(item: VaultItem, onDelete: () -> Unit, onOpenDetail: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        AppIcon(item.packageName, size = 34.dp)
        Column(Modifier.weight(1f)) {
            val head = buildString {
                append(item.group ?: friendlyPackage(item.packageName))
                item.sender?.takeIf { it.isNotBlank() && it != item.group }?.let { append(" · "); append(it) }
            }
            Text(head, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    item.mode == "sender_only" -> "Conteúdo oculto — só o remetente"
                    !item.body.isNullOrBlank() -> item.body
                    !item.title.isNullOrBlank() -> item.title
                    else -> "—"
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    shortTime(item.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = NotifyShareColors.muted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenDetail).padding(vertical = 2.dp),
                ) {
                    Icon(
                        NotifyIcons.MoreVertical,
                        contentDescription = null,
                        tint = NotifyShareColors.muted,
                        modifier = Modifier.size(14.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    Text("mais detalhes", style = MaterialTheme.typography.labelSmall, color = NotifyShareColors.muted)
                }
            }
        }
        Icon(
            NotifyIcons.Close,
            contentDescription = "Remover",
            tint = NotifyShareColors.muted,
            modifier = Modifier.clickable(onClick = onDelete).padding(4.dp),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VaultDetailSheet(item: VaultItem, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                AppIcon(item.packageName, size = 40.dp)
                Column {
                    Text(
                        item.group ?: friendlyPackage(item.packageName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        com.notifyshare.ui.common.appLabel(item.packageName),
                        style = MaterialTheme.typography.labelMedium,
                        color = NotifyShareColors.muted,
                    )
                }
            }

            DetailLine("Tipo", if (item.eventType == "system") "Alerta do sistema" else "Mensagem")
            item.group?.let { DetailLine("Grupo", it) }
            item.sender?.let { DetailLine("Remetente", it) }
            DetailLine("Quando aconteceu", fullDateTime(item.occurredAt))
            DetailLine("Guardado em", fullDateTime(item.savedAt))
            DetailLine(
                "Conteúdo guardado",
                when {
                    item.mode == "sender_only" -> "Só o remetente (sem texto)"
                    !item.body.isNullOrBlank() -> item.body
                    !item.title.isNullOrBlank() -> item.title
                    else -> "—"
                },
            )
            if (!item.title.isNullOrBlank() && item.title != item.body) {
                DetailLine("Título original", item.title)
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
