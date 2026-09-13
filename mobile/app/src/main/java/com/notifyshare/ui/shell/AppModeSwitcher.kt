package com.notifyshare.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notifyshare.data.local.AppMode

/**
 * Alterna entre o modo Social (precisa de conta) e o Local (cofre + permissões
 * do aparelho, sem conta). Fica sempre no topo, acima de qualquer tela — não
 * depende de estar logado, e sobrevive a trocar/adicionar conta.
 *
 * Segmentado, não dropdown: só há duas opções mutuamente exclusivas, então
 * mostrar as duas + o estado atual sempre visíveis vale mais que economizar
 * espaço atrás de um menu que precisaria de 2 toques para fazer a mesma troca.
 */
@Composable
fun AppModeSwitcher(mode: AppMode, onChange: (AppMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .width(220.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .padding(3.dp),
        ) {
            Segment("Social", mode == AppMode.SOCIAL, Modifier.weight(1f)) { onChange(AppMode.SOCIAL) }
            Segment("Local", mode == AppMode.LOCAL, Modifier.weight(1f)) { onChange(AppMode.LOCAL) }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(17.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
