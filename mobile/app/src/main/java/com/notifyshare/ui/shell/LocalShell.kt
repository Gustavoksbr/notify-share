package com.notifyshare.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.notifyshare.AppContainer
import com.notifyshare.data.local.AppMode
import com.notifyshare.ui.common.tr
import com.notifyshare.ui.settings.ConfiguracoesNav
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.vault.VaultAppsNav
import com.notifyshare.ui.vault.VaultTimelineScreen

/**
 * Modo Local: cofre ("Salvos") + Permissões do aparelho. Funciona sem conta e
 * sem internet — é a metade "sozinho" do app, peer do modo Social (o switcher
 * no topo troca entre os dois), não uma aba escondida dentro de um dos dois.
 */
@Composable
fun LocalShell(container: AppContainer, mode: AppMode, onModeChange: (AppMode) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // "menu" no toque normal da aba; "permissions" quando um aviso amarelo leva
    // direto pra lá, sem passar pelo menu de Configurações.
    var configuracoesStart by rememberSaveable { mutableStateOf("menu") }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(NotifyIcons.Bookmark, tr("Salvos", "Saved")) },
                    label = { Text(tr("Salvos", "Saved")) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(NotifyIcons.Sliders, "Apps") },
                    label = { Text("Apps") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2; configuracoesStart = "menu" },
                    icon = { Icon(NotifyIcons.Wrench, tr("Configurações", "Settings")) },
                    label = { Text(tr("Configurações", "Settings")) },
                    colors = colors,
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            com.notifyshare.ui.common.AppWarningBanners(
                container,
                onOpenPermissions = { configuracoesStart = "permissions"; tab = 2 },
            )
            AppModeSwitcher(mode, onModeChange)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> VaultTimelineScreen(container)
                    1 -> VaultAppsNav(container)
                    else -> ConfiguracoesNav(container, startDestination = configuracoesStart)
                }
            }
        }
    }
}
