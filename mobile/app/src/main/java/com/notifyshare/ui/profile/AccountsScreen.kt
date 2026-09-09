package com.notifyshare.ui.profile

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.AuthRepository
import com.notifyshare.data.local.AccountInfo
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountsViewModel(private val auth: AuthRepository) : ViewModel() {

    val accounts: StateFlow<List<AccountInfo>> = MutableStateFlow<List<AccountInfo>>(emptyList()).also { flow ->
        viewModelScope.launch { auth.accounts.collect { flow.value = it } }
    }.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
}

@Composable
fun AccountsScreen(
    vm: AccountsViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as? NotifyShareApp
    var confirmRemove by remember { mutableStateOf<AccountInfo?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Contas", onBack = onBack)

        LazyColumn(Modifier.fillMaxSize()) {
            val (google, password) = accounts.partition { it.google }

            if (google.isNotEmpty()) {
                item { SectionLabel("Contas Google · entram na hora") }
                items(google, key = { it.id }) { acc ->
                    AccountRow(
                        acc,
                        onSwitch = {
                            app?.switchAccount(acc.id) {
                                com.notifyshare.ui.restartUiForAccountChange(context, "feed")
                            }
                        },
                        onRemove = { confirmRemove = acc },
                    )
                }
            }
            if (password.isNotEmpty()) {
                item { SectionLabel("Contas com senha") }
                items(password, key = { it.id }) { acc ->
                    AccountRow(
                        acc,
                        onSwitch = {
                            app?.switchAccount(acc.id) {
                                com.notifyshare.ui.restartUiForAccountChange(context, "feed")
                            }
                        },
                        onRemove = { confirmRemove = acc },
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddAccount)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Icon(NotifyIcons.Plus, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Adicionar conta", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    confirmRemove?.let { acc ->
        val entrar = if (acc.google) "com o Google" else "com a senha"
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remover @${acc.nickname}?") },
            text = {
                Text(
                    if (acc.active) {
                        "Você está usando esta conta agora. Ao remover, ela sai deste aparelho e " +
                            "você volta para a tela de contas. Para usá-la aqui de novo é só entrar " +
                            "$entrar. Nada é apagado no servidor."
                    } else {
                        "A conta sai da lista deste aparelho. Você vai precisar entrar de novo " +
                            "$entrar para usá-la aqui outra vez. Nada é apagado no servidor."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = acc
                    confirmRemove = null
                    app?.removeAccount(target.id) { removedActive, stillLoggedIn ->
                        if (removedActive) {
                            com.notifyshare.ui.restartUiForAccountChange(
                                context, if (stillLoggedIn) "accounts" else null,
                            )
                        }
                    }
                }) {
                    Text("Remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AccountRow(acc: AccountInfo, onSwitch: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !acc.active, onClick = onSwitch)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Avatar(acc.nickname)
        Column(Modifier.weight(1f)) {
            Text("@${acc.nickname}", style = MaterialTheme.typography.titleMedium)
            Text(acc.email, style = MaterialTheme.typography.labelSmall, color = NotifyShareColors.muted)
        }
        if (acc.active) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Ativa",
                    style = MaterialTheme.typography.labelMedium,
                    color = NotifyShareColors.online,
                )
                TextButton(onClick = onRemove) { Text("Remover", color = NotifyShareColors.muted) }
            }
        } else {
            TextButton(onClick = onRemove) { Text("Remover", color = NotifyShareColors.muted) }
        }
    }
}
