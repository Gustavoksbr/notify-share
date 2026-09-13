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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.local.AppMode
import com.notifyshare.ui.onboarding.PermissionsScreen
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.vault.VaultAppsNav
import com.notifyshare.ui.vault.VaultTimelineScreen
import kotlinx.coroutines.launch

/**
 * Modo Local: cofre ("Salvos") + Permissões do aparelho. Funciona sem conta e
 * sem internet — é a metade "sozinho" do app, peer do modo Social (o switcher
 * no topo troca entre os dois), não uma aba escondida dentro de um dos dois.
 */
@Composable
fun LocalShell(container: AppContainer, mode: AppMode, onModeChange: (AppMode) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceEnabled by container.serviceSwitch.enabled.collectAsStateWithLifecycle(initialValue = true)

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
                    icon = { Icon(NotifyIcons.Bookmark, "Salvos") },
                    label = { Text("Salvos") },
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
                    onClick = { tab = 2 },
                    icon = { Icon(NotifyIcons.Check, "Permissões") },
                    label = { Text("Permissões") },
                    colors = colors,
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AppModeSwitcher(mode, onModeChange)
            com.notifyshare.ui.common.AppWarningBanners(container)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> VaultTimelineScreen(container)
                    1 -> VaultAppsNav(container)
                    else -> PermissionsScreen(
                        fcmStatus = null,
                        onBack = null,
                        serviceEnabled = serviceEnabled,
                        onSetService = { on ->
                            scope.launch {
                                container.serviceSwitch.set(on)
                                (context.applicationContext as? NotifyShareApp)?.refreshSharing()
                            }
                        },
                    )
                }
            }
        }
    }
}
