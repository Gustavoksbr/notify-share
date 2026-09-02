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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    fun remove(id: String) = viewModelScope.launch { auth.removeAccount(id) }
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

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Contas", onBack = onBack)

        LazyColumn(Modifier.fillMaxSize()) {
            val (google, password) = accounts.partition { it.google }

            if (google.isNotEmpty()) {
                item { SectionLabel("Contas Google · entram na hora") }
                items(google, key = { it.id }) { acc ->
                    AccountRow(acc, onSwitch = { app?.switchAccount(acc.id) { recreate(context) } }, onRemove = { vm.remove(acc.id) })
                }
            }
            if (password.isNotEmpty()) {
                item { SectionLabel("Contas com senha") }
                items(password, key = { it.id }) { acc ->
                    AccountRow(acc, onSwitch = { app?.switchAccount(acc.id) { recreate(context) } }, onRemove = { vm.remove(acc.id) })
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
            Text("Ativa", style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.online)
        } else {
            TextButton(onClick = onRemove) { Text("Remover", color = NotifyShareColors.muted) }
        }
    }
}

private fun recreate(context: android.content.Context) {
    (context as? android.app.Activity)?.recreate()
}
