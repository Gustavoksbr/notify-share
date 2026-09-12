package com.notifyshare.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.NotifyShareApp
import com.notifyshare.core.Connectivity
import com.notifyshare.core.NetState
import com.notifyshare.core.NotificationAccess
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * Faixa de avisos no topo — a mesma em toda a app (logado e deslogado). Ficam
 * empilhados num Column, um em cima do outro, nunca sobrepostos.
 *
 * `onOpenPermissions` = null em quem não tem essa rota (shell deslogado): aí o
 * aviso de acesso às notificações não aparece (não há compartilhamento ativo
 * sem login de qualquer forma).
 */
@Composable
fun AppWarningBanners(
    container: AppContainer,
    onOpenPermissions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceEnabled by container.serviceSwitch.enabled.collectAsStateWithLifecycle(initialValue = true)
    val netState by Connectivity.state.collectAsStateWithLifecycle()
    val hasActiveShare by container.shareState.hasActiveShare.collectAsStateWithLifecycle(initialValue = false)

    var nlsGranted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        nlsGranted = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxWidth()) {
        val netBanner = when (netState) {
            NetState.NO_INTERNET -> "Você está sem internet"
            NetState.SERVER_DOWN -> "Servidor fora do ar — tentando reconectar"
            NetState.OK -> null
        }
        if (netBanner != null) {
            Banner(
                netBanner,
                bg = MaterialTheme.colorScheme.surfaceContainerHigh,
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!serviceEnabled) {
            Banner(
                "⚠️ O segundo plano está desativado — o compartilhamento em tempo real e os " +
                    "alertas de \"Meu celular\" estão parados. Toque para religar.",
                bg = NotifyShareColors.warning,
                fg = Color(0xFF452B00),
                onClick = {
                    scope.launch {
                        container.serviceSwitch.set(true)
                        (context.applicationContext as? NotifyShareApp)?.refreshSharing()
                    }
                },
            )
        }

        if (!nlsGranted && hasActiveShare && onOpenPermissions != null) {
            Banner(
                "⚠️ O compartilhamento de apps não está funcionando: falta o acesso às " +
                    "notificações. Toque para ativar.",
                bg = MaterialTheme.colorScheme.errorContainer,
                fg = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onOpenPermissions,
            )
        }
    }
}

@Composable
private fun Banner(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
