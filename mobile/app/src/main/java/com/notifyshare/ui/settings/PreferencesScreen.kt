package com.notifyshare.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.AppContainer
import com.notifyshare.ui.common.ScreenTitle
import com.notifyshare.ui.common.SectionLabel
import com.notifyshare.ui.common.tr
import com.notifyshare.ui.theme.NotifyShareColors
import kotlinx.coroutines.launch

/**
 * Preferências do app — hoje só o idioma da interface. Separada de
 * Permissões de propósito: uma é sobre autorização do sistema, a outra é
 * gosto do usuário; cresce sozinha sem precisar reaproveitar espaço de outra
 * tela.
 *
 * O idioma aqui só troca o texto das telas que já foram migradas para [tr]
 * (esta, o menu de Configurações e a barra de baixo do modo Local) — o resto
 * do app ainda é só em português. Ver `ideias/` para o plano de tradução
 * completa.
 */
@Composable
fun PreferencesScreen(container: AppContainer, onBack: () -> Unit) {
    val lang by container.language.flow.collectAsStateWithLifecycle(initialValue = "pt")
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        ScreenTitle(tr("Preferências", "Preferences"), onBack = onBack)

        SectionLabel(tr("Idioma", "Language"))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            LanguageOption("pt", "Português", lang, Modifier.weight(1f)) {
                scope.launch { container.language.set("pt") }
            }
            LanguageOption("en", "English", lang, Modifier.weight(1f)) {
                scope.launch { container.language.set("en") }
            }
        }
        Text(
            tr(
                "Só as telas novas (esta e o menu de Configurações) já traduzem. O resto do app continua em português por enquanto.",
                "Only the new screens (this one and the Settings menu) translate so far. The rest of the app is still Portuguese-only for now.",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = NotifyShareColors.muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LanguageOption(
    value: String,
    label: String,
    selected: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val on = value == selected
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
