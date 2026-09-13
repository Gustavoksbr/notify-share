package com.notifyshare.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Calendário completo (dia, mês, ano) para escolher um período exato — só faz
 * sentido para o cofre porque tudo aqui é local, sem paginação de servidor.
 *
 * Tela cheia com uma barra fixa no topo (Cancelar/Confirmar): o cabeçalho
 * padrão do DateRangePicker ("8 de ago. de 2026 - 17 de set. de 2026", em 3
 * linhas) e os botões embaixo do calendário ficavam fora da tela em aparelhos
 * comuns — aqui os botões nunca saem de vista.
 *
 * Tocar o mesmo dia duas vezes escolhe um único dia; tocar só um dia e
 * confirmar filtra "a partir dali".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (from: LocalDate, to: LocalDate?) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.toUtcMillis(),
        initialSelectedEndDateMillis = initialTo?.toUtcMillis(),
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.weight(1f))
                    Text("Escolher período", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = state.selectedStartDateMillis != null,
                        onClick = {
                            val start = state.selectedStartDateMillis
                            if (start != null) {
                                onConfirm(start.toLocalDate(), state.selectedEndDateMillis?.toLocalDate())
                            }
                        },
                    ) { Text("Confirmar") }
                }

                // Sem isto, "qual dia é o inicio e qual e o fim" nao fica obvio —
                // o cabecalho padrao do componente (que explicaria) foi removido
                // por ficar feio/cortado (ver comentario da funcao).
                Text(
                    rangeHint(state.selectedStartDateMillis, state.selectedEndDateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )

                DateRangePicker(
                    state = state,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val hintFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Deixa explícito que o 1º toque é o início do período e o 2º é o fim. */
private fun rangeHint(startMillis: Long?, endMillis: Long?): String {
    val start = startMillis?.toLocalDate()
    val end = endMillis?.toLocalDate()
    return when {
        start == null -> "Toque no primeiro dia do período que você quer filtrar."
        end == null || end == start -> {
            "Início: ${start.format(hintFormatter)}. Toque em outro dia para marcar o fim do " +
                "período, ou confirme para filtrar só esse dia."
        }
        else -> "Período: de ${start.format(hintFormatter)} (início) até ${end.format(hintFormatter)} (fim)."
    }
}
