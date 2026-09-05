package com.notifyshare

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notifyshare.ui.auth.AuthViewModel
import com.notifyshare.ui.auth.LoginScreen
import com.notifyshare.ui.auth.RegisterScreen
import com.notifyshare.ui.shell.MainShell
import com.notifyshare.ui.theme.NotifyShareTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as NotifyShareApp).container
        val openTarget = intent?.getStringExtra(EXTRA_OPEN)

        setContent {
            NotifyShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    NotifyShareRoot(container, openTarget)
                }
            }
        }
    }

    companion object {
        /** "feed" | "chat:<nickname>" | "grants" — de onde a notificacao veio. */
        const val EXTRA_OPEN = "open"
    }
}

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PRIVACY = "privacy"
}

@Composable
private fun NotifyShareRoot(container: AppContainer, openTarget: String?) {
    val hasSession by container.authRepository.hasSession.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    // "Adicionar conta": mostra o login mesmo com uma sessao ativa.
    var addingAccount by remember { mutableStateOf(false) }

    // Criado sempre (barato). Antes ficava depois do early-return, e o
    // authComplete de um login anterior ainda ficava true — ao "adicionar conta"
    // o LaunchedEffect disparava na hora e voltava pra home sem mostrar o login.
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(container) { AuthViewModel.Factory(container.authRepository) },
    )
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    // Android 13+: pede permissao de notificacao uma vez, ja logado.
    var askedForNotifications by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(hasSession) {
        if (hasSession && !askedForNotifications && Build.VERSION.SDK_INT >= 33) {
            askedForNotifications = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Adicionar conta: ao autenticar, recria a Activity para tudo reler do zero
    // (mesmo caminho da troca de conta). No login normal, hasSession ja resolve.
    LaunchedEffect(authState.authComplete) {
        if (authState.authComplete && addingAccount) {
            addingAccount = false
            com.notifyshare.ui.restartUiForAccountChange(context)
        }
    }

    if (hasSession && !addingAccount) {
        MainShell(
            container = container,
            openTarget = openTarget,
            onAddAccount = {
                // limpa authComplete/senha do login anterior antes de abrir o form
                authViewModel.resetForm()
                addingAccount = true
            },
        )
        return
    }

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
                onCancel = if (addingAccount) ({ addingAccount = false }) else null,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onGoToLogin = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            com.notifyshare.ui.settings.PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
