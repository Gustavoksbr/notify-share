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
 * Sub-navegação do cofre — usada como conteúdo de uma aba nos dois shells
 * (logado e deslogado). Timeline ↔ regras ↔ seletor de apps ↔ alertas do sistema.
 */
@Composable
fun VaultNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "timeline") {
        composable("timeline") {
            VaultTimelineScreen(container, onOpenRules = { nav.navigate("rules") })
        }
        composable("rules") {
            val vm: RulesViewModel = viewModel(factory = VaultRulesViewModelFactory(container))
            RulesScreen(
                vm = vm,
                nickname = "o cofre",
                title = "O que guardar",
                onBack = { nav.popBackStack() },
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
