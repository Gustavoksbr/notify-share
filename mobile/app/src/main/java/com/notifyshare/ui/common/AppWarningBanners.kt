package com.notifyshare.ui.common

import android.content.Context
import android.os.PowerManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.core.Connectivity
import com.notifyshare.core.NetState
import com.notifyshare.core.NotificationAccess
import com.notifyshare.ui.theme.NotifyShareColors

/**
 * Faixa de avisos no topo — a mesma em toda a app (logado e deslogado). Ficam
 * empilhados num Column, um em cima do outro, nunca sobrepostos.
 *
 * Só avisos de permissões "para enviar" (capturar/compartilhar) moram aqui —
 * valem pros dois modos (Social e Local), por isso ficam ACIMA do
 * [com.notifyshare.ui.shell.AppModeSwitcher] nas telas que o chamam. O aviso
 * de "para receber" (mostrar notificações) é só do Social — ver
 * [ReceiveWarningBanner], chamado embaixo do switch.
 *
 * `onOpenPermissions` = null em quem não tem essa rota (shell deslogado): aí o
 * aviso de acesso às notificações não aparece.
 *
 * Nenhum desses avisos depende de ter compartilhamento ativo nem do modo:
 * sem segundo plano, acesso às notificações ou isenção de bateria, nada é
 * capturado direito — nem para o Social nem para o cofre local. Por isso um
 * card só (aqui), reaproveitado nos dois, em vez de cada tela ter o seu.
 */
@Composable
fun AppWarningBanners(
    container: AppContainer,
    onOpenPermissions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val serviceEnabled by container.serviceSwitch.enabled.collectAsStateWithLifecycle(initialValue = true)
    val netState by Connectivity.state.collectAsStateWithLifecycle()

    var nlsGranted by remember { mutableStateOf(true) }
    var batteryExempt by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        nlsGranted = NotificationAccess.isGranted(context)
        batteryExempt = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
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

        if (!serviceEnabled && onOpenPermissions != null) {
            Banner(
                "⚠️ Segundo plano desativado — toque para religar.",
                bg = NotifyShareColors.warning,
                fg = Color(0xFF452B00),
                onClick = onOpenPermissions,
            )
        }

        if (!nlsGranted && onOpenPermissions != null) {
            Banner(
                "⚠️ Acesso às notificações desativado — toque para religar.",
                bg = NotifyShareColors.warning,
                fg = Color(0xFF452B00),
                onClick = onOpenPermissions,
            )
        }

        if (!batteryExempt && onOpenPermissions != null) {
            Banner(
                "⚠️ Ignorar economia de bateria desativado — toque para religar.",
                bg = NotifyShareColors.warning,
                fg = Color(0xFF452B00),
                onClick = onOpenPermissions,
            )
        }
    }
}

/**
 * Aviso de "mostrar notificações" (POST_NOTIFICATIONS) — só faz sentido no
 * Social: é o que deixa o app te avisar quando alguém compartilha algo com
 * você. Fica embaixo do [com.notifyshare.ui.shell.AppModeSwitcher] (ver
 * [AppWarningBanners] para os avisos de "enviar", que ficam em cima).
 */
@Composable
fun ReceiveWarningBanner(onOpenPermissions: (() -> Unit)? = null) {
    if (android.os.Build.VERSION.SDK_INT < 33 || onOpenPermissions == null) return
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        canPost = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    if (!canPost) {
        Banner(
            "⚠️ Mostrar notificações desativado — toque para religar.",
            bg = NotifyShareColors.warning,
            fg = Color(0xFF452B00),
            onClick = onOpenPermissions,
        )
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
