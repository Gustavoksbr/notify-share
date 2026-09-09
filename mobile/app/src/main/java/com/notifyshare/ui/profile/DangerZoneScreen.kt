package com.notifyshare.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.ui.common.OutlinedActionButton
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * Só as duas ações destrutivas e irreversíveis. Fica um passo além do Perfil de
 * propósito — não é algo pra aparecer de cara junto com Permissões/Privacidade.
 * As duas confirmações oferecem "Fazer backup" antes de seguir, e exigem digitar
 * o próprio nickname — mais uma medida de segurança contra toque acidental.
 */
@Composable
fun DangerZoneScreen(
    vm: ProfileViewModel,
    onBack: () -> Unit,
    onDeleteHistory: suspend () -> Boolean,
    onAccountDeleted: (stillLoggedIn: Boolean) -> Unit,
    onExport: (suspend () -> String?)? = null,
    onSaveExport: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    var historyBusy by remember { mutableStateOf(false) }
    var historyNotice by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Zona de perigo", onBack = onBack)

        Text(
            "As duas ações abaixo são irreversíveis.",
            style = MaterialTheme.typography.bodyMedium,
            color = NotifyShareColors.muted,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedActionButton(
                "Apagar meu histórico",
                onClick = { confirmHistory = true },
                enabled = !historyBusy,
                loading = historyBusy,
                modifier = Modifier.fillMaxWidth(),
                textColor = MaterialTheme.colorScheme.error,
            )
            historyNotice?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.muted)
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            OutlinedActionButton(
                "Excluir minha conta",
                onClick = { confirmDelete = true },
                enabled = !state.deleting,
                loading = state.deleting,
                modifier = Modifier.fillMaxWidth(),
                textColor = MaterialTheme.colorScheme.error,
            )
        }
    }

    fun backup(onDone: () -> Unit = {}) {
        if (onExport == null) return
        scope.launch {
            val data = onExport()
            if (data != null) onSaveExport(data)
            onDone()
        }
    }

    if (confirmHistory) {
        ConfirmWithBackupDialog(
            title = "Apagar o histórico?",
            body = "Todas as notificações que você compartilhou somem — inclusive do feed de " +
                "quem recebeu. Não dá para desfazer.",
            nickname = state.nickname,
            confirmLabel = "Apagar",
            showBackup = onExport != null,
            onDismiss = { confirmHistory = false },
            onBackup = { backup() },
            onConfirm = {
                confirmHistory = false
                historyBusy = true
                scope.launch {
                    historyNotice = if (onDeleteHistory()) "Histórico apagado." else "Não deu para apagar agora."
                    historyBusy = false
                }
            },
        )
    }

    if (confirmDelete) {
        ConfirmWithBackupDialog(
            title = "Excluir sua conta?",
            body = "Isto apaga para sempre: seus amigos, compartilhamentos, regras, mensagens e " +
                "todo o histórico. Não dá para desfazer.",
            nickname = state.nickname,
            confirmLabel = "Excluir",
            showBackup = onExport != null,
            deleting = state.deleting,
            onDismiss = { confirmDelete = false },
            onBackup = { backup() },
            onConfirm = {
                confirmDelete = false
                vm.deleteAccount(onAccountDeleted)
            },
        )
    }
}

/**
 * Confirmação de 3 botões: Cancelar, Fazer backup (não fecha o diálogo — dá pra
 * seguir depois) e a ação destrutiva, liberada só quando o nickname digitado bate.
 */
@Composable
private fun ConfirmWithBackupDialog(
    title: String,
    body: String,
    nickname: String,
    confirmLabel: String,
    showBackup: Boolean,
    deleting: Boolean = false,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var backedUp by remember { mutableStateOf(false) }
    val matches = typed.trim() == nickname

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.error) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Para confirmar, digite \"$nickname\":",
                    style = MaterialTheme.typography.labelMedium,
                    color = NotifyShareColors.muted,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        onClick = onConfirm,
                        enabled = matches && !deleting,
                    ) {
                        Text(
                            confirmLabel,
                            color = if (matches) MaterialTheme.colorScheme.error else NotifyShareColors.muted,
                            fontWeight = if (matches) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        )
                    }
                }

                if (showBackup) {
                    OutlinedActionButton(
                        if (backedUp) "Backup solicitado ✓" else "Fazer backup antes",
                        onClick = { backedUp = true; onBackup() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
