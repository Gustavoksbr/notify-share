package com.notifyshare.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notifyshare.core.NotificationAccess

/**
 * Card de aviso mostrado nas telas de regras/seletor de apps quando o acesso a
 * notificacoes nao esta concedido — sem ele, nenhum app escolhido aqui vai
 * capturar nada.
 */
@Composable
fun NotificationAccessWarning(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        granted = NotificationAccess.isGranted(context)
        onPauseOrDispose { }
    }
    if (granted) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
            .clickable { context.startActivity(NotificationAccess.settingsIntent()) }
            .padding(14.dp),
    ) {
        Text(
            "Falta o acesso às notificações",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            "Sem essa permissão, os apps que você escolher aqui não capturam nada. " +
                "Toque para abrir as Configurações e ativar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
