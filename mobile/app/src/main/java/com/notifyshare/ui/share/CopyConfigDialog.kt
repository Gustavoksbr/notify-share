package com.notifyshare.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.theme.NotifyShareColors

/** O que copiar de outro compartilhamento para o grant/cofre atual. */
sealed interface CopyTarget {
    /** Todas as regras de app. */
    data object All : CopyTarget
    /** A config de um app só (modo, remetentes, filtros). */
    data class App(val pkg: String) : CopyTarget
    /** Só a lista de contatos liberados de um app. */
    data class Senders(val pkg: String) : CopyTarget
}

/**
 * Escolhe um compartilhamento de origem e copia a config dele para cá. Não
 * salva sozinho — só marca como alteração pendente, o usuário revisa e salva.
 */
@Composable
fun CopyConfigDialog(
    target: CopyTarget,
    grants: List<GrantDto>,
    loading: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (sourceGrantId: String, replace: Boolean) -> Unit,
) {
    var picked by remember { mutableStateOf<GrantDto?>(null) }
    val chosen = picked
    val offersMergeChoice = target is CopyTarget.All || target is CopyTarget.Senders

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (target) {
                    is CopyTarget.All -> "Copiar configuração de apps"
                    is CopyTarget.App -> "Copiar config deste app"
                    is CopyTarget.Senders -> "Copiar contatos"
                },
            )
        },
        text = {
            when {
                loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Carregando compartilhamentos…")
                }

                grants.isEmpty() -> Text(
                    "Você não tem outro compartilhamento para copiar a configuração.",
                )

                chosen == null -> Column {
                    Text(
                        "De qual compartilhamento?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NotifyShareColors.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(grants, key = { it.id }) { g ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { picked = g }
                                    .padding(vertical = 10.dp),
                            ) {
                                Avatar(g.counterpart, size = 34)
                                Column(Modifier.weight(1f)) {
                                    Text("@${g.counterpart}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${g.enabledApps} apps" +
                                            if (g.hasSpecificSenders) " · remetentes específicos" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NotifyShareColors.muted,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> Column {
                    Text(
                        when (target) {
                            is CopyTarget.All ->
                                "Trazer as regras de app de @${chosen.counterpart}."
                            is CopyTarget.App ->
                                "Trazer a config deste app como está em @${chosen.counterpart}. " +
                                    "Isso sobrescreve o que está aqui para este app."
                            is CopyTarget.Senders ->
                                "Trazer os contatos liberados deste app de @${chosen.counterpart}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (offersMergeChoice) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Mesclar mantém o que já existe aqui; Substituir troca tudo pelo do outro.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NotifyShareColors.muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                chosen == null -> {}
                offersMergeChoice -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy, onClick = { onConfirm(chosen.id, false) }) { Text("Mesclar") }
                    TextButton(enabled = !busy, onClick = { onConfirm(chosen.id, true) }) { Text("Substituir") }
                }
                else -> TextButton(enabled = !busy, onClick = { onConfirm(chosen.id, false) }) { Text("Copiar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (chosen == null) "Fechar" else "Cancelar") }
        },
    )
}
