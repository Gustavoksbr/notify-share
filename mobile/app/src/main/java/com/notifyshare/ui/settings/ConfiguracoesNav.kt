package com.notifyshare.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notifyshare.AppContainer
import com.notifyshare.NotifyShareApp
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.tr
import com.notifyshare.ui.onboarding.PermissionsScreen
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * Sub-navegação de "Configurações" do modo Local: por ora só o menu ↔
 * Permissões. Preferências (idioma) fica pronta mas escondida — ver o
 * comentário mais abaixo.
 */
@Composable
fun ConfiguracoesNav(container: AppContainer, startDestination: String = "menu") {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = startDestination) {
        composable("menu") {
            ConfiguracoesMenuScreen(
                onOpenPermissions = { nav.navigate("permissions") },
                onOpenPreferences = { nav.navigate("preferences") },
            )
        }
        composable("permissions") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val serviceEnabled by container.serviceSwitch.enabled.collectAsStateWithLifecycle(initialValue = true)
            PermissionsScreen(
                fcmStatus = null,
                onBack = { nav.popBackStack() },
                serviceEnabled = serviceEnabled,
                onSetService = { on ->
                    scope.launch {
                        container.serviceSwitch.set(on)
                        (context.applicationContext as? NotifyShareApp)?.refreshSharing()
                    }
                },
            )
        }
        // Preferências (idioma PT/EN) escondida por ora — traduzir o app inteiro
        // é trabalho grande, fica pra depois. A tela e o LanguageStore continuam
        // prontos; é só descomentar aqui e a linha em ConfiguracoesMenuScreen.
        // composable("preferences") {
        //     PreferencesScreen(container = container, onBack = { nav.popBackStack() })
        // }
    }
}

@Composable
private fun ConfiguracoesMenuScreen(onOpenPermissions: () -> Unit, onOpenPreferences: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ScreenTitle(tr("Configurações", "Settings"))
        ConfiguracoesRow(
            tr("Permissões", "Permissions"),
            tr("Acesso a notificações e bateria", "Notification and battery access"),
            onOpenPermissions,
        )
        // ConfiguracoesRow(
        //     tr("Preferências", "Preferences"),
        //     tr("Idioma da interface", "Interface language"),
        //     onOpenPreferences,
        // )
    }
}

@Composable
private fun ConfiguracoesRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.muted)
        }
        Icon(NotifyIcons.Chevron, null, tint = NotifyShareColors.muted)
    }
}
