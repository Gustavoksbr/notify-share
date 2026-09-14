package com.notifyshare.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.notifyshare.data.FeedQuery

/**
 * Painel de filtros das telas de notificacoes (feed geral e por pessoa).
 * `people` vazio esconde a secao "De quem" (na tela por pessoa nao faz sentido).
 * A lista de apps e paginada/buscavel porque pode crescer sem limite.
 * Reaproveitado tambem pra "exportar com filtro" ([confirmLabel] muda pra "Exportar").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFiltersSheet(
    current: FeedQuery,
    people: List<String>,
    packages: List<String>,
    senders: List<String> = emptyList(),
    confirmLabel: String = "Aplicar",
    onDismiss: () -> Unit,
    onApply: (FeedQuery) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var appQuery by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Conteúdo rolável + rodapé fixo: com muitos remetentes/apps marcados, o
        // conteúdo pode passar da altura da folha — sem scroll aqui, o botão de
        // confirmar ficava fora da tela (parecia ter sumido).
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Filtros", style = MaterialTheme.typography.titleLarge)

                SectionLabel("Período")
                ChipRow(
                    options = listOf("all" to "Tudo", "today" to "Hoje", "7d" to "7 dias"),
                    selected = draft.period,
                    onSelect = { draft = draft.copy(period = it ?: "all") },
                    allowNone = false,
                )

                SectionLabel("Tipo")
                ChipRow(
                    options = listOf(
                        "message" to "Mensagens", "battery" to "Bateria",
                        "network" to "Rede", "system" to "Sistema",
                    ),
                    selected = draft.type,
                    onSelect = { draft = draft.copy(type = it) },
                    allowNone = true,
                )

                if (people.isNotEmpty()) {
                    SectionLabel("De quem")
                    ChipRow(
                        options = people.map { it to "@$it" },
                        selected = draft.from,
                        onSelect = { draft = draft.copy(from = it) },
                        allowNone = true,
                    )
                }

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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                FilterChip(
                                    selected = on,
                                    onClick = { draft = draft.copy(packageName = if (on) null else pkg) },
                                    label = { Text(prettyPackage(pkg)) },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Limpar") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    allowNone: Boolean,
) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = selected == value
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on && allowNone) null else value) },
                label = { Text(label) },
            )
        }
    }
}

fun prettyPackage(pkg: String): String = when {
    pkg.startsWith("system:") -> pkg.removePrefix("system:").replaceFirstChar { it.uppercase() }
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
