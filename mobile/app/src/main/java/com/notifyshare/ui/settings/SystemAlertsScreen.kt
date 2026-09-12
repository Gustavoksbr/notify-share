package com.notifyshare.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.data.local.SystemAlerts
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * Ajustes de "Meu celular": quais alertas do proprio aparelho viram
 * notificacoes. Global — vale para todo compartilhamento que inclui o app
 * "Meu celular". So dispara com o servico em segundo plano ativo.
 */
@Composable
fun SystemAlertsScreen(container: AppContainer, onBack: () -> Unit) {
    val cfg by container.systemAlerts.flow.collectAsStateWithLifecycle(initialValue = SystemAlerts())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun edit(block: (SystemAlerts) -> SystemAlerts) = scope.launch { container.systemAlerts.update(block) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Alertas do celular", onBack = onBack)

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Vale para todos os compartilhamentos que incluem \"Meu celular\". " +
                    "Só funciona com o compartilhamento em segundo plano ligado.",
                style = MaterialTheme.typography.labelMedium,
                color = NotifyShareColors.muted,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )

            ToggleRow(
                "Bateria baixa",
                "Avisa quando cair para ${cfg.batteryLowPct}%",
                cfg.batteryLow,
            ) { on -> edit { it.copy(batteryLow = on) } }
            if (cfg.batteryLow) {
                PctRow("Avisar em", cfg.batteryLowPct, 5..40, 5) { v ->
                    edit { it.copy(batteryLowPct = v) }
                }
            }

            ToggleRow(
                "Bateria carregada",
                "Avisa quando subir para ${cfg.batteryHighPct}%",
                cfg.batteryHigh,
            ) { on -> edit { it.copy(batteryHigh = on) } }
            if (cfg.batteryHigh) {
                PctRow("Avisar em", cfg.batteryHighPct, 50..100, 5) { v ->
                    edit { it.copy(batteryHighPct = v) }
                }
            }

            ToggleRow(
                "Carregador",
                "Avisa ao conectar e ao desconectar da tomada",
                cfg.charging,
            ) { on -> edit { it.copy(charging = on) } }

            ToggleRow(
                "Celular reiniciou",
                "Avisa quando o aparelho liga de novo",
                cfg.reboot,
            ) { on -> edit { it.copy(reboot = on) } }

            ToggleRow(
                "Resumo de bateria do dia",
                "Uma vez por dia: quanto de bateria o celular consumiu",
                cfg.dailyDrain,
            ) { on -> edit { it.copy(dailyDrain = on) } }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = NotifyShareColors.muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PctRow(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 34.dp, end = 22.dp, bottom = 10.dp),
    ) {
        Text("$label $value%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Stepper("−") { onChange((value - step).coerceIn(range.first, range.last)) }
        Stepper("+") { onChange((value + step).coerceIn(range.first, range.last)) }
    }
}

@Composable
private fun Stepper(symbol: String, onClick: () -> Unit) {
    Text(
        symbol,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
