package com.notifyshare.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notifyshare.ui.common.ChipRow
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.common.prettyPackage

/**
 * Filtros do cofre — mesma anatomia do [com.notifyshare.ui.common.NotificationFiltersSheet]
 * do feed social, mais o período exato (calendário), possível só porque tudo
 * aqui é local. Reaproveitada tanto pra filtrar a timeline quanto pra escolher
 * o que exportar (o [confirmLabel] muda conforme o uso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFiltersSheet(
    current: VaultQuery,
    packages: List<String>,
    senders: List<String>,
    confirmLabel: String = "Aplicar",
    onDismiss: () -> Unit,
    onApply: (VaultQuery) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var appQuery by remember { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Filtros", style = MaterialTheme.typography.titleLarge)

            SectionLabel("Período")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Tudo", "today" to "Hoje", "7d" to "7 dias").forEach { (value, label) ->
                    val on = draft.period == value
                    FilterChip(
                        selected = on,
                        onClick = { draft = draft.copy(period = value) },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = draft.period == "custom",
                    onClick = { showCalendar = true },
                    label = {
                        Text(
                            if (draft.period == "custom" && draft.fromDate != null) {
                                if (draft.toDate != null && draft.toDate != draft.fromDate) {
                                    "${draft.fromDate} a ${draft.toDate}"
                                } else {
                                    "${draft.fromDate}"
                                }
                            } else {
                                "Escolher data…"
                            },
                        )
                    },
                )
            }

            SectionLabel("Tipo")
            ChipRow(
                options = listOf("message" to "Mensagens", "system" to "Alertas do sistema"),
                selected = draft.type,
                onSelect = { draft = draft.copy(type = it) },
                allowNone = true,
            )

            if (senders.isNotEmpty()) {
                SectionLabel("Remetente")
                ChipRow(
                    options = senders.map { it to it },
                    selected = draft.sender,
                    onSelect = { draft = draft.copy(sender = it) },
                    allowNone = true,
                )
            }

            if (packages.isNotEmpty()) {
                SectionLabel("App")
                if (packages.size > 8) {
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        placeholder = { Text("Buscar app") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val shown = packages
                    .filter { appQuery.isBlank() || prettyPackage(it).contains(appQuery, ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(shown, key = { it }) { pkg ->
                        val on = draft.packageName == pkg
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            FilterChip(
                                selected = on,
                                onClick = { draft = draft.copy(packageName = if (on) null else pkg) },
                                label = { Text(prettyPackage(pkg)) },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Limpar") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
            }
        }
    }

    if (showCalendar) {
        VaultDateRangeDialog(
            initialFrom = draft.fromDate,
            initialTo = draft.toDate,
            onDismiss = { showCalendar = false },
            onConfirm = { from, to ->
                draft = draft.copy(period = "custom", fromDate = from, toDate = to ?: from)
                showCalendar = false
            },
        )
    }
}
