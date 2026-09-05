package com.notifyshare.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notifyshare.ui.common.OutlinedActionButton
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * O que fica no aparelho, o que o servidor guarda, o que nunca sai — mais a
 * unica acao aqui, que e reversivel/segura: baixar os dados (portabilidade).
 * Apagar historico e excluir conta (destrutivas, irreversiveis) ficam juntas
 * na Zona de perigo do Perfil — nao faz sentido a mesma acao morar nos dois
 * lugares.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onExport: (suspend () -> String?)? = null,
    onSaveExport: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Privacidade e dados", onBack = onBack)

        Block(
            "No seu aparelho",
            "Suas notificações são lidas aqui, localmente. Só sai o que uma regra sua manda, " +
                "e só para quem você escolheu.",
            NotifyShareColors.online,
        )
        Block(
            "No servidor",
            "• Notificações compartilhadas: 7 dias, depois apagam sozinhas.\n" +
                "• Mensagens do chat: até você apagar.\n" +
                "• Nickname, e-mail (nunca mostrado), amigos e compartilhamentos.",
            MaterialTheme.colorScheme.primary,
        )
        Block(
            "Nunca sai daqui",
            "Apps que não estão numa regra, apps desligados, sua localização, seus contatos.",
            NotifyShareColors.muted,
        )

        notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (onExport != null) {
            OutlinedActionButton(
                "Baixar meus dados",
                onClick = {
                    busy = true
                    scope.launch {
                        val data = onExport()
                        notice = if (data != null) {
                            onSaveExport(data); "Arquivo gerado."
                        } else "Não deu para gerar agora."
                        busy = false
                    }
                },
                loading = busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }

        Text(
            "Apagar histórico e excluir conta ficam em Perfil → Zona de perigo.",
            style = MaterialTheme.typography.labelSmall,
            color = NotifyShareColors.muted,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Block(title: String, body: String, accent: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel(title, modifier = Modifier.padding(0.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
