package com.notifyshare.ui.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notifyshare.AppContainer
import com.notifyshare.ui.VaultRulesViewModelFactory
import com.notifyshare.ui.share.AppPickerScreen
import com.notifyshare.ui.share.RulesScreen
import com.notifyshare.ui.share.RulesViewModel

/**
 * Sub-navegação de "o que o cofre guarda": regras ↔ seletor de apps ↔ alertas
 * do sistema. É a aba "Apps" do modo Local, separada de "Salvos" (a timeline
 * em si) — configurar o quê guardar não precisa ficar grudado em ver o que já
 * foi guardado.
 */
@Composable
fun VaultAppsNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "rules") {
        composable("rules") {
            val vm: RulesViewModel = viewModel(factory = VaultRulesViewModelFactory(container))
            RulesScreen(
                vm = vm,
                nickname = "o cofre",
                title = "Apps",
                onBack = null,
                onPickApps = { nav.navigate("apps") },
                onOpenSystemAlerts = { nav.navigate("system-alerts") },
            )
        }
        composable("apps") {
            val rulesEntry = remember(it) { nav.getBackStackEntry("rules") }
            val vm: RulesViewModel = viewModel(
                viewModelStoreOwner = rulesEntry,
                factory = VaultRulesViewModelFactory(container),
            )
            AppPickerScreen(
                vm = vm,
                nickname = "o cofre",
                title = "Adicionar ao cofre",
                onBack = { nav.popBackStack() },
            )
        }
        composable("system-alerts") {
            com.notifyshare.ui.settings.SystemAlertsScreen(container, onBack = { nav.popBackStack() })
        }
    }
}
