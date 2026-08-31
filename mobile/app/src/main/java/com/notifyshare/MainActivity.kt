package com.notifyshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notifyshare.data.AuthRepository
import com.notifyshare.ui.auth.AuthViewModel
import com.notifyshare.ui.auth.LoginScreen
import com.notifyshare.ui.auth.RegisterScreen
import com.notifyshare.ui.home.HomeScreen
import com.notifyshare.ui.home.HomeViewModel
import com.notifyshare.ui.theme.NotifyShareTheme

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as NotifyShareApp).container.authRepository

        setContent {
            NotifyShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    NotifyShareRoot(repository)
                }
            }
        }
    }
}

/**
 * A sessao decide a tela, nao a navegacao.
 *
 * `hasSession` vem do DataStore, entao qualquer coisa que limpe os tokens —
 * logout, refresh recusado, familia revogada por reuso — leva o usuario de
 * volta ao login sozinha, sem ninguem precisar chamar navigate.
 */
@Composable
private fun NotifyShareRoot(repository: AuthRepository) {
    val hasSession by repository.hasSession.collectAsStateWithLifecycle(initialValue = false)

    if (hasSession) {
        val homeViewModel: HomeViewModel = viewModel(
            factory = remember(repository) { HomeViewModel.Factory(repository) }
        )
        HomeScreen(homeViewModel)
        return
    }

    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(repository) { AuthViewModel.Factory(repository) }
    )

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = authViewModel,
                onGoToLogin = { navController.popBackStack() },
            )
        }
    }
}
