package com.notifyshare.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notifyshare.data.ApiResult
import com.notifyshare.data.AuthRepository
import com.notifyshare.data.remote.UserDto
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val user: UserDto? = null,
    val errorMessage: String? = null,
)

class HomeViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = repository.me()) {
                is ApiResult.Ok -> HomeUiState(loading = false, user = result.value)
                is ApiResult.Failure -> HomeUiState(loading = false, errorMessage = result.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository) as T
    }
}

/**
 * Tela temporaria: existe para provar que o ciclo inteiro fecha — cadastro,
 * token guardado cifrado, requisicao autenticada e resposta do servidor.
 * Sai do lugar quando o Feed real chegar.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.user != null -> LoggedIn(state.user!!, viewModel::logout)

            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = state.errorMessage ?: "Nao foi possivel carregar o perfil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = viewModel::load) {
                    Text("Tentar de novo", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = viewModel::logout) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LoggedIn(user: UserDto, onLogout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Text(
                    text = user.nickname.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column {
                Text("@${user.nickname}", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = NotifyShareColors.muted,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(NotifyShareColors.onlineContainer, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(NotifyShareColors.online, CircleShape)
            )
            Text(
                text = "Sessao autenticada com o backend",
                style = MaterialTheme.typography.bodySmall,
                color = NotifyShareColors.online,
            )
        }

        Text(
            text = "A partir daqui entram o Feed, os amigos e as regras de " +
                "compartilhamento. Esta tela existe so para provar que o ciclo " +
                "de autenticacao fecha de ponta a ponta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onLogout) {
            Text("Sair desta conta", color = MaterialTheme.colorScheme.error)
        }
    }
}
