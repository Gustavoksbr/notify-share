package com.notifyshare.ui.share

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.local.RecentApp
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val packageName: String, val label: String)

private val SYNTHETIC = listOf(
    AppEntry("system:battery", "Bateria"),
    AppEntry("system:wifi", "Wi-Fi"),
)

@Composable
fun AppPickerScreen(vm: RulesViewModel, nickname: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as? NotifyShareApp)?.container
    var query by remember { mutableStateOf("") }

    val state by vm.state.collectAsStateWithLifecycle()
    val alreadyAdded = remember(state.rules) { state.rules.map { it.packageName }.toSet() }

    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            runCatching {
                pm.getInstalledApplications(0)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    val recent by produceState(initialValue = emptyList<RecentApp>(), container) {
        value = container?.recentApps?.recent().orEmpty()
    }

    val labelByPkg = remember(apps) { apps.associate { it.packageName to it.label } }

    val recentEntries = remember(recent, alreadyAdded, labelByPkg) {
        recent.filter { it.packageName !in alreadyAdded && it.packageName in labelByPkg }
            .map { it to AppEntry(it.packageName, labelByPkg[it.packageName] ?: it.packageName) }
    }
    val systemEntries = remember(alreadyAdded) { SYNTHETIC.filter { it.packageName !in alreadyAdded } }
    val allEntries = remember(apps, alreadyAdded) { apps.filter { it.packageName !in alreadyAdded } }

    val matchesQuery = { e: AppEntry -> query.isBlank() || e.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Apps para @$nickname", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(if (apps.isEmpty()) "Buscar app" else "Buscar entre ${apps.size} apps") },
            leadingIcon = { Icon(NotifyIcons.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (apps.isEmpty()) {
            LoadingBox()
            return@Column
        }

        // adiciona e mantém a tela aberta: dá pra escolher vários de uma vez.
        // O app escolhido sai da lista (passa a "já adicionado") como confirmação.
        val add: (String) -> Unit = { pkg -> vm.addApp(pkg) }

        LazyColumn {
            val recentShown = recentEntries.filter { matchesQuery(it.second) }
            if (recentShown.isNotEmpty()) {
                item { SectionLabel("Notificaram você nos últimos 7 dias") }
                items(recentShown, key = { "r_${it.second.packageName}" }) { (rec, entry) ->
                    AppRow(entry, subtitle = "${rec.count} notificação${if (rec.count == 1) "" else "s"}") {
                        add(entry.packageName)
                    }
                }
            }

            val sysShown = systemEntries.filter { matchesQuery(it) }
            if (sysShown.isNotEmpty()) {
                item { SectionLabel("Sistema") }
                items(sysShown, key = { "s_${it.packageName}" }) { AppRow(it) { add(it.packageName) } }
            }

            val allShown = allEntries.filter { matchesQuery(it) }
            item { SectionLabel("Todos os apps · ${allShown.size}") }
            items(allShown, key = { "a_${it.packageName}" }) { AppRow(it) { add(it.packageName) } }
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        com.notifyshare.ui.common.AppIcon(entry.packageName, size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = NotifyShareColors.muted)
            }
        }
        Icon(NotifyIcons.Plus, "Adicionar", tint = MaterialTheme.colorScheme.primary)
    }
}
