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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.data.ApiResult
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.remote.GrantDto
import com.notifyshare.data.remote.UserProfileDto
import com.notifyshare.ui.common.Avatar
import com.notifyshare.ui.common.ErrorRetry
import com.notifyshare.ui.common.LoadingBox
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfileDto? = null,
    val failedToLoad: Boolean = false,
    val working: Boolean = false,
    val refreshing: Boolean = false,
    /** mensagem curta apos oferecer/pedir compartilhamento */
    val notice: String? = null,
)

class UserProfileViewModel(
    private val repo: SocialRepository,
    val nickname: String,
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() {
        com.notifyshare.core.Connectivity.probeBeforeRefresh()
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = _state.value.profile == null)
        viewModelScope.launch {
            when (val r = repo.userProfile(nickname)) {
                is ApiResult.Ok ->
                    // preserva o aviso — recarregar nao pode apagar o "pedido enviado"
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        profile = r.value,
                        failedToLoad = false,
                    )
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        failedToLoad = _state.value.profile == null,
                    )
            }
        }
    }

    private fun signalSocial() {
        com.notifyshare.core.AppEvents.signal(com.notifyshare.core.AppEvents.FRIENDS)
        com.notifyshare.core.AppEvents.signal(com.notifyshare.core.AppEvents.GRANTS)
    }

    fun block() = viewModelScope.launch {
        _state.value = _state.value.copy(working = true)
        repo.block(nickname)
        _state.value = _state.value.copy(working = false)
        load()
        signalSocial()
    }

    fun unblock() = viewModelScope.launch {
        _state.value = _state.value.copy(working = true)
        repo.unblock(nickname)
        _state.value = _state.value.copy(working = false)
        load()
        signalSocial()
    }

    /** Peço para @nickname compartilhar as notificações dele comigo. */
    fun requestShare() = viewModelScope.launch {
        _state.value = _state.value.copy(working = true, notice = null)
        val r = repo.requestGrant(nickname)
        _state.value = _state.value.copy(
            working = false,
            notice = when {
                r is ApiResult.Ok && r.value.status == "active" -> "Compartilhamento ativado."
                r is ApiResult.Ok -> "Pedido enviado para @$nickname."
                r is ApiResult.Failure -> r.message
                else -> null
            },
        )
        load()
        signalSocial()
    }

    /** Ofereço compartilhar as minhas notificações com @nickname. */
    fun offerShare() = viewModelScope.launch {
        _state.value = _state.value.copy(working = true, notice = null)
        val r = repo.offerGrant(nickname)
        _state.value = _state.value.copy(
            working = false,
            notice = when {
                r is ApiResult.Ok && r.value.status == "active" -> "Compartilhamento ativado."
                r is ApiResult.Ok -> "Oferta enviada para @$nickname."
                r is ApiResult.Failure -> r.message
                else -> null
            },
        )
        load()
        signalSocial()
    }

    /** Cancela um pedido/oferta pendente que eu iniciei. */
    fun cancelPending(grantId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(working = true, notice = null)
        val r = repo.declineGrant(grantId)
        _state.value = _state.value.copy(
            working = false,
            notice = if (r is ApiResult.Failure) r.message else "Pedido cancelado.",
        )
        load()
        signalSocial()
    }

    fun dismissNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun UserProfileScreen(
    vm: UserProfileViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmBlock by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Perfil", onBack = onBack)

        when {
            state.loading -> LoadingBox()
            state.failedToLoad || state.profile == null ->
                ErrorRetry("Não foi possível carregar este perfil.", vm::load)
            else -> {
                val p = state.profile!!
                com.notifyshare.ui.common.PullRefresh(state.refreshing, vm::refresh, Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    ) {
                        Avatar(p.nickname, size = 84)
                        Text("@${p.nickname}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                p.blockedByMe -> "Você bloqueou esta pessoa"
                                p.friend -> "Vocês são amigos"
                                else -> "Vocês não são amigos"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (p.blockedByMe) MaterialTheme.colorScheme.error
                            else NotifyShareColors.muted,
                        )
                        if (p.friend && !p.blockedByMe) {
                            com.notifyshare.ui.common.PillButton(
                                "Enviar mensagem",
                                onClick = { onOpenChat(p.nickname) },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    // futuramente: foto de perfil salva pelo usuário

                    state.notice?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { vm.dismissNotice() }
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                        ) {
                            Text(
                                msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "OK",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    GrantSection("Você compartilha com @${p.nickname}", p.sharingWithThem)
                    GrantSection("@${p.nickname} compartilha com você", p.receivingFromThem)

                    if (p.friend && !p.blockedByMe) {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("Compartilhamento")

                        val pendingRequest = p.outgoingPending.firstOrNull { it.status == "requested_by_recipient" }
                        val pendingOffer = p.outgoingPending.firstOrNull { it.status == "offered_by_sharer" }

                        when {
                            pendingRequest != null -> PendingRow(
                                "Pedido para receber de @${p.nickname} enviado",
                                enabled = !state.working,
                            ) { vm.cancelPending(pendingRequest.id) }
                            p.receivingFromThem.isEmpty() -> com.notifyshare.ui.common.OutlinedActionButton(
                                "Pedir para receber as notificações de @${p.nickname}",
                                onClick = { vm.requestShare() },
                                loading = state.working,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                            )
                        }

                        when {
                            pendingOffer != null -> PendingRow(
                                "Oferta para @${p.nickname} enviada",
                                enabled = !state.working,
                            ) { vm.cancelPending(pendingOffer.id) }
                            p.sharingWithThem.isEmpty() -> com.notifyshare.ui.common.OutlinedActionButton(
                                "Oferecer minhas notificações para @${p.nickname}",
                                onClick = { vm.offerShare() },
                                loading = state.working,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (p.blockedByMe) {
                        com.notifyshare.ui.common.OutlinedActionButton(
                            "Desbloquear",
                            onClick = { vm.unblock() },
                            loading = state.working,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        )
                    } else {
                        com.notifyshare.ui.common.OutlinedActionButton(
                            "Bloquear",
                            onClick = { confirmBlock = true },
                            enabled = !state.working,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                            textColor = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Bloquear @${vm.nickname}?") },
            text = {
                Text(
                    "Vocês não vão mais conseguir trocar mensagens. Ela vai saber que foi " +
                        "bloqueada se tentar te enviar algo. Você pode desbloquear depois.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    vm.block()
                }) { Text("Bloquear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PendingRow(text: String, enabled: Boolean, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        com.notifyshare.ui.common.InlineActionButton(
            "Cancelar",
            onCancel,
            loading = !enabled,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun GrantSection(title: String, grants: List<GrantDto>) {
    SectionLabel(title)
    if (grants.isEmpty()) {
        Text(
            "Nada por aqui.",
            style = MaterialTheme.typography.bodySmall,
            color = NotifyShareColors.muted,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
        return
    }
    grants.forEach { g ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    grantStatusLabel(g.status),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${g.enabledApps} app${if (g.enabledApps == 1) "" else "s"} liberado" +
                        if (g.enabledApps == 1) "" else "s",
                    style = MaterialTheme.typography.labelSmall,
                    color = NotifyShareColors.muted,
                )
            }
        }
    }
}

private fun grantStatusLabel(status: String): String = when (status) {
    "active" -> "Ativo"
    "paused_by_sharer", "paused_by_recipient" -> "Pausado"
    "offered_by_sharer" -> "Oferta pendente"
    "requested_by_recipient" -> "Pedido pendente"
    else -> status
}
