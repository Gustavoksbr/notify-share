package com.notifyshare.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notifyshare.core.OemSettings
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.theme.NotifyShareColors

@Composable
fun PermissionsScreen(fcmStatus: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var notificationAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    val autostart = remember { OemSettings.autostartIntent(context) }

    // Reavalia ao voltar das Configuracoes do sistema.
    LifecycleResumeEffect(Unit) {
        notificationAccess = hasNotificationAccess(context)
        batteryExempt = isBatteryExempt(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Permissões", onBack = onBack)

        Text(
            "O Android exige que você conceda cada uma manualmente. Toque em qualquer " +
                "uma para abrir as Configurações e ligar ou desligar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        PermissionCard(
            title = "Acesso às notificações",
            body = "Suas notificações são lidas no próprio aparelho para aplicar as regras que você criar. " +
                "Nada sai daqui sem uma regra sua.",
            granted = notificationAccess,
            required = true,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )

        // Android 13+ bloqueia essa permissao para apps instalados fora da Play
        // Store ("configuracao restrita"). So aparece se for o caso e ainda nao concedido.
        if (!notificationAccess && !installedFromPlayStore(context)) {
            Text(
                "Se o Android disser \"configuração restrita\" e não deixar ativar: Configurações → " +
                    "Apps → Notify Share → menu (⋮) no canto → \"Permitir configurações restritas\". " +
                    "Depois volte aqui. Apps da Play Store não precisam disso.",
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.warning,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
        }

        PermissionCard(
            title = "Ignorar economia de bateria",
            body = "Sem isso o sistema corta a internet do app quando a tela apaga, e os avisos chegam horas depois.",
            granted = batteryExempt,
            required = true,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        if (autostart != null) {
            PermissionCard(
                title = "Início automático (opcional)",
                body = "Deixa o app se reabrir sozinho e receber avisos em segundo plano de forma mais confiável. " +
                    "Sem isso o app funciona normalmente — só enquanto estiver aberto.",
                granted = null, // o sistema nao diz se esta ligado; e so um atalho
                required = false,
                onClick = { runCatching { context.startActivity(autostart) } },
            )
        }

        DeliveryStatus(fcmStatus)

        Text(
            "Enquanto houver compartilhamento ativo, uma notificação fixa fica visível no seu aparelho. " +
                "É assim que você sabe que está ligado.",
            style = MaterialTheme.typography.labelMedium,
            color = NotifyShareColors.muted,
            modifier = Modifier
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                .padding(14.dp),
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean?,
    required: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val (label, color) = when {
            granted == true -> "Concedida · toque para gerenciar" to NotifyShareColors.online
            granted == false && required -> "Conceder" to MaterialTheme.colorScheme.primary
            else -> "Abrir configuração" to MaterialTheme.colorScheme.primary
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (granted == true) NotifyShareColors.online else MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (granted == true) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(24.dp),
                )
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun DeliveryStatus(fcmStatus: String?) {
    val (text, color) = when (fcmStatus) {
        "ok" -> "Entrega em segundo plano: ativa" to NotifyShareColors.online
        "blocked" -> "Entrega em segundo plano indisponível neste aparelho. As notificações " +
            "aparecem quando você abre o app. O registro tenta de novo sozinho." to NotifyShareColors.warning
        else -> "Entrega em segundo plano: configurando..." to NotifyShareColors.muted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private fun hasNotificationAccess(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.contains(context.packageName) }
}

private fun isBatteryExempt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun installedFromPlayStore(context: Context): Boolean = runCatching {
    val installer = if (android.os.Build.VERSION.SDK_INT >= 30) {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)
    }
    installer == "com.android.vending"
}.getOrDefault(false)
