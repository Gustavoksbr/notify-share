package com.notifyshare.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.AuthRepository
import com.notifyshare.data.DeviceRepository
import com.notifyshare.data.local.JsonCache
import com.notifyshare.data.remote.UserDto
import com.notifyshare.session.SessionState
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.StatusBanner
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    /** local-first: nickname/email vem do aparelho, nunca "carrega". */
    val nickname: String = "",
    val email: String = "",
    val google: Boolean = false,
    /** true so enquanto ainda nao lemos o aparelho (instantaneo na pratica). */
    val loading: Boolean = true,
    val refreshFailed: Boolean = false,
    val refreshing: Boolean = false,
    val deleting: Boolean = false,
)

class ProfileViewModel(
    private val auth: AuthRepository,
    private val devices: DeviceRepository,
    private val session: SessionState,
    private val cache: JsonCache,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.activeAccount.collect { acc ->
                if (acc != null) {
                    _state.value = _state.value.copy(
                        nickname = acc.nickname,
                        email = acc.email,
                        google = acc.google,
                        loading = false,
                    )
                }
            }
        }
        load()
    }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    /** Atualiza em segundo plano; falha nao apaga os dados locais. */
    fun load() {
        viewModelScope.launch {
            when (val r = auth.me()) {
                is ApiResult.Ok -> {
                    session.set(r.value)
                    devices.flushPendingToken(null)
                    _state.value = _state.value.copy(
                        nickname = r.value.nickname,
                        email = r.value.email,
                        google = r.value.google,
                        loading = false,
                        refreshFailed = false,
                        refreshing = false,
                    )
                }
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(
                        loading = false, refreshFailed = true, refreshing = false,
                    )
            }
        }
    }

    /** Exclui a conta no servidor e depois limpa a sessao local. */
    fun deleteAccount(then: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(deleting = true)
        val r = auth.deleteAccount()
        _state.value = _state.value.copy(deleting = false)
        if (r is ApiResult.Ok) {
            session.clear()
            cache.clear()
            auth.logout()
            then()
        }
    }
}

@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onOpenPermissions: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onOpenDangerZone: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.loading) {
        LoadingBox()
        return
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Perfil")

        com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh, Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Avatar(state.nickname.ifBlank { "?" }, size = 72)
            Text("@${state.nickname}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${state.email} · privado",
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.muted,
            )
        }

        StatusBanner(
            if (state.refreshFailed) "Sem conexão" else "Ativo",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        ProfileRow("Trocar de conta", "Contas neste aparelho", onOpenAccounts)
        ProfileRow("Permissões", "Acesso a notificações e bateria", onOpenPermissions)
        ProfileRow("Privacidade e dados", "O que sai do aparelho, exportar dados", onOpenPrivacy)
        ProfileRow("Zona de perigo", "Excluir conta e apagar histórico", onOpenDangerZone)

        Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NotifyShareColors.muted)
        }
        Icon(NotifyIcons.Chevron, null, tint = NotifyShareColors.muted)
    }
}
