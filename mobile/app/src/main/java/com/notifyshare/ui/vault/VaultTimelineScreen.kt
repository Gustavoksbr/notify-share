package com.notifyshare.ui.vault

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.data.local.VaultItem
import com.notifyshare.ui.common.AppIcon
import com.notifyshare.ui.common.EmptyState
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.shareTextExport
import com.notifyshare.ui.format.friendlyPackage
import com.notifyshare.ui.format.fullDateTime
import com.notifyshare.ui.format.shortTime
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * O "cofre": tudo que as regras locais capturaram, mais novo no topo. Vive só
 * neste aparelho. `onOpenRules` abre a config de o quê guardar.
 */
@Composable
fun VaultTimelineScreen(container: AppContainer, onOpenRules: () -> Unit) {
    val items by container.vault.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf<VaultItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Salvos",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty()) {
                        Text(
                            "Exportar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    shareTextExport(
                                        context, "notify-share-cofre.txt", buildVaultExport(items),
                                    )
                                }
                                .padding(8.dp),
                        )
                    }
                    Text(
                        "Configurar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenRules).padding(8.dp),
                    )
                }
            },
        )

        if (items.isEmpty()) {
            EmptyState(
                "Nada guardado ainda.\nToque em \"Configurar\" e escolha o que este " +
                    "aparelho deve guardar — só aqui, nunca na nuvem.",
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                VaultRow(item, onDelete = { confirmDelete = item })
            }
            item {
                Text(
                    "Limpar tudo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmClear = true }
                        .padding(20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar o cofre?") },
            text = { Text("Apaga tudo que está guardado neste aparelho. Não dá para desfazer.") },
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

private fun buildVaultExport(items: List<VaultItem>): String = buildString {
    appendLine("Cofre — Notify Share")
    appendLine("Exportado em ${fullDateTime(java.time.Instant.now().toString())}")
    appendLine("${items.size} itens")
    appendLine()
    items.sortedBy { it.occurredAt }.forEach { i ->
        val head = buildString {
            append(i.group ?: friendlyPackage(i.packageName))
            i.sender?.takeIf { it.isNotBlank() && it != i.group }?.let { append(" · "); append(it) }
        }
        appendLine("[${fullDateTime(i.occurredAt)}] $head")
        val line = when {
            i.mode == "sender_only" -> "(conteúdo oculto — só o remetente)"
            !i.body.isNullOrBlank() -> i.body
            !i.title.isNullOrBlank() -> i.title
            else -> "—"
        }
        appendLine("  $line")
        appendLine()
    }
}

@Composable
private fun VaultRow(item: VaultItem, onDelete: () -> Unit) {
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
            Text(
                shortTime(item.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
            )
        }
        Icon(
            NotifyIcons.Close,
            contentDescription = "Remover",
            tint = NotifyShareColors.muted,
            modifier = Modifier.clickable(onClick = onDelete).padding(4.dp),
        )
    }
}
