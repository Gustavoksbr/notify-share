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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.notifyshare.core.OemSettings
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.theme.NotifyShareColors

@Composable
fun PermissionsScreen(
    fcmStatus: String?,
    onBack: (() -> Unit)? = null,
    onSendTest: (suspend () -> Int?)? = null,
    serviceEnabled: Boolean = true,
    onSetService: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var notificationAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    var canPostNotifications by remember { mutableStateOf(canPostNotifications(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    val autostart = remember { OemSettings.autostartIntent(context) }

    // Reavalia ao voltar das Configuracoes do sistema.
    LifecycleResumeEffect(Unit) {
        notificationAccess = hasNotificationAccess(context)
        canPostNotifications = canPostNotifications(context)
        batteryExempt = isBatteryExempt(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Permissões", onBack = onBack)

        // Explicacao da legenda de cores (verde/ambar) — linguagem de dev, tirada
        // da tela pro usuario final. O codigo fica pronto se quisermos voltar.
        /*
        Text(
            buildAnnotatedString {
                append("Tudo ")
                withStyle(SpanStyle(color = NotifyShareColors.online)) { append("verde") }
                append(" = está tudo ligado. Tudo ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("âmbar") }
                append(" = o app não tem nenhuma permissão.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        */

        // Interruptor mestre: liga/desliga o servico direto, nao abre config.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Compartilhamento em segundo plano", style = MaterialTheme.typography.titleMedium)
                    ScopeTag("para enviar")
                    Text(
                        "Mantém suas notificações sendo compartilhadas mesmo com o app fechado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = serviceEnabled,
                    onCheckedChange = onSetService,
                )
            }
        }

        PermissionCard(
            title = "Acesso às notificações",
            scope = "para enviar",
            body = "Deixa o app ver suas notificações para compartilhar com seus amigos as que você escolher.",
            granted = notificationAccess,
            required = true,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )

        // Android 13+: permissao de POSTAR notificacoes (diferente de LER as dos
        // outros apps). Sem ela o sistema bloqueia os avisos do Notify Share.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            PermissionCard(
                title = "Mostrar notificações",
                scope = "para receber",
                body = "Deixa o app te avisar quando um amigo compartilhar uma notificação com você.",
                granted = canPostNotifications,
                required = true,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }

        // Android 13+ bloqueia essa permissao para apps instalados fora da Play
        // Store ("configuracao restrita"). So aparece se for o caso e ainda nao concedido.
        if (!notificationAccess && !installedFromPlayStore(context)) {
            Text(
                "Se aparecer \"configuração restrita\" e não deixar ativar: Configurações → " +
                    "Apps → Notify Share → menu (⋮) no canto → \"Permitir configurações restritas\". " +
                    "Depois volte aqui.",
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
            scope = "enviar e receber",
            body = "Faz as notificações chegarem na hora, mesmo com o celular parado ou a tela apagada.",
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
                scope = "enviar e receber",
                body = "Faz o app voltar a funcionar sozinho depois que você reinicia o celular.",
                granted = null, // o sistema nao expoe esse estado pra nenhum app de terceiros; nao e bug nosso
                required = false,
                onClick = { runCatching { context.startActivity(autostart) } },
            )
        }

        // Redundante com o interruptor "Compartilhamento em segundo plano" que ja
        // fica no topo da tela — mais um status pra explicar sem precisar.
        // DeliveryStatus(fcmStatus)

        // Diagnostico pensado pra debugar, nao pra tela do usuario final. Fica
        // comentado em vez de apagado — reativa trocando isto de volta.
        /*
        if (onSendTest != null) {
            TestNotificationCard(
                notificationAccess = notificationAccess,
                canPost = canPostNotifications,
                fcmOk = fcmStatus == "ok",
                onSendTest = onSendTest,
            )
        }
        */

        // Text(
        //     "Enquanto houver compartilhamento ativo, uma notificação fixa fica visível no seu aparelho. " +
        //         "É assim que você sabe que está ligado.",
        //     style = MaterialTheme.typography.labelMedium,
        //     color = NotifyShareColors.muted,
        //     modifier = Modifier
        //         .padding(16.dp)
        //         .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
        //         .padding(14.dp),
        // )
    }
}

/** Tag discreta que diz se a permissao serve pro envio, pro recebimento ou os dois. */
@Composable
private fun ScopeTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean?,
    required: Boolean,
    scope: String? = null,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            if (scope != null) ScopeTag(scope)
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // granted == null: nao sabemos o estado real (ex.: inicio automatico —
        // nenhum app de terceiros consegue ler isso). Nao mostra como pendente
        // (ambar), pra nao parecer que falta algo quando pode nao faltar.
        val label = when {
            granted == true -> "Concedida · toque para gerenciar"
            granted == false && required -> "Conceder"
            else -> "Gerenciar"
        }
        val textColor = when (granted) {
            true -> NotifyShareColors.online
            false -> MaterialTheme.colorScheme.onPrimary
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val background = when (granted) {
            true -> MaterialTheme.colorScheme.surface
            false -> MaterialTheme.colorScheme.primary
            null -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(background, RoundedCornerShape(24.dp))
                .padding(vertical = 12.dp),
        )
    }
}

/**
 * Diagnostico de ponta a ponta: mostra o estado das 3 permissoes que a entrega
 * depende e dispara um evento de teste pelo pipeline real.
 */
@Composable
private fun TestNotificationCard(
    notificationAccess: Boolean,
    canPost: Boolean,
    fcmOk: Boolean,
    onSendTest: suspend () -> Int?,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Testar o compartilhamento", style = MaterialTheme.typography.titleMedium)
        CheckLine("Acesso às notificações", notificationAccess)
        CheckLine("Permitir notificações", canPost)
        CheckLine("Entrega em segundo plano", fcmOk)

        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp))
                .clickable(enabled = !sending) {
                    sending = true
                    result = null
                    scope.launch {
                        val n = onSendTest()
                        result = when {
                            n == null -> "Não deu para enviar agora. Tente de novo."
                            n == 0 -> "Enviado ✓ — mas ninguém tem um compartilhamento ativo com você ainda."
                            n == 1 -> "Enviado ✓ — 1 pessoa recebeu agora."
                            else -> "Enviado ✓ — $n pessoas receberam agora."
                        }
                        sending = false
                    }
                }
                .padding(vertical = 12.dp),
        ) {
            if (sending) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    "Enviar notificação de teste",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!notificationAccess) {
            Text(
                "⚠️ Sem o \"Acesso às notificações\", nenhuma notificação de app é compartilhada — " +
                    "só bateria e Wi-Fi.",
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.warning,
            )
        }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✓" else "✗", color = if (ok) NotifyShareColors.online else MaterialTheme.colorScheme.error)
        Text(label, style = MaterialTheme.typography.bodyMedium)
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

private fun canPostNotifications(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

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
