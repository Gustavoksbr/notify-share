package com.notifyshare

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.launch

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
    const val RECOVER = "recover"
}

@Composable
private fun NotifyShareRoot(container: AppContainer, openTarget: String?) {
    // Social x Local: independente de login, sobrevive a trocar/adicionar conta.
    val mode by container.appMode.flow.collectAsStateWithLifecycle(
        initialValue = com.notifyshare.data.local.AppMode.SOCIAL,
    )
    val modeScope = androidx.compose.runtime.rememberCoroutineScope()
    val onModeChange: (com.notifyshare.data.local.AppMode) -> Unit = { m ->
        modeScope.launch { container.appMode.set(m) }
    }

    // Idioma da interface (PT/EN): a UI (Preferências) e a persistência
    // (LanguageStore) já existem prontas, só a troca de verdade fica pra depois
    // — traduzir as ~150 telas do app é trabalho grande demais pra fazer agora.
    // Pra religar: descomente as 3 linhas abaixo (o resto já está pronto).
    // val lang by container.language.flow.collectAsStateWithLifecycle(initialValue = "pt")
    // CompositionLocalProvider(com.notifyshare.ui.common.LocalAppLanguage provides lang) {
    NotifyShareRootContent(container, openTarget, mode, onModeChange)
    // }
}

@Composable
private fun NotifyShareRootContent(
    container: AppContainer,
    openTarget: String?,
    mode: com.notifyshare.data.local.AppMode,
    onModeChange: (com.notifyshare.data.local.AppMode) -> Unit,
) {
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
            // Toda autenticacao cai no feed (a Activity recriada le esse alvo).
            com.notifyshare.ui.restartUiForAccountChange(context, openTarget = "feed")
        }
    }

    // Modo Local: cofre + permissões, sem conta. Independente de login —
    // ganha de qualquer estado de sessão.
    if (mode == com.notifyshare.data.local.AppMode.LOCAL) {
        com.notifyshare.ui.shell.LocalShell(container, mode = mode, onModeChange = onModeChange)
        return
    }

    if (hasSession && !addingAccount) {
        MainShell(
            container = container,
            // Depois de qualquer login/cadastro, ignora deep link antigo e vai pro feed.
            openTarget = openTarget?.takeIf { !authState.authComplete },
            onAddAccount = {
                // limpa authComplete/senha do login anterior antes de abrir o form
                authViewModel.resetForm()
                addingAccount = true
            },
            mode = mode,
            onModeChange = onModeChange,
        )
        return
    }

    // "Adicionar conta": formulário em tela cheia, com Cancelar — não o shell,
    // sem o switcher (fluxo dedicado, preso a já estar logado em outra conta).
    if (addingAccount) {
        AuthNavHost(authViewModel, addingAccount = true, onCancelAdd = { addingAccount = false })
        return
    }

    // Social deslogado: switcher no topo + login preenchendo o resto — sem
    // barra de baixo (só volta quando o usuário logar).
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            com.notifyshare.ui.common.AppWarningBanners(container)
            com.notifyshare.ui.shell.AppModeSwitcher(mode, onModeChange)
        }
        Box(Modifier.weight(1f)) {
            AuthNavHost(authViewModel, addingAccount = false, onCancelAdd = {})
        }
    }
}

@Composable
private fun AuthNavHost(
    authViewModel: AuthViewModel,
    addingAccount: Boolean,
    onCancelAdd: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.RECOVER) },
                onCancel = if (addingAccount) onCancelAdd else null,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onGoToLogin = { navController.popBackStack() },
                onOpenPrivacy = { com.notifyshare.ui.common.openUrl(context, com.notifyshare.ui.common.PRIVACY_POLICY_URL) },
            )
        }
        composable(Routes.RECOVER) {
            com.notifyshare.ui.auth.RecoverPasswordScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack(Routes.LOGIN, inclusive = false) },
            )
        }
    }
}
