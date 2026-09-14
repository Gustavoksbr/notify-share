package com.notifyshare.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notifyshare.ui.theme.NotifyShareColors

/**
 * Rodapé de paginação por número de página — "Página X de Y · N no total" mais
 * uma fileira de números clicáveis. Compartilhado por Salvos, feed geral e feed
 * por pessoa, pra dar a mesma navegação nos três.
 */
@Composable
fun PageBar(
    page: Int,
    totalPages: Int,
    totalCount: Int,
    show: Boolean,
    onPage: (Int) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (show) {
            Text(
                if (totalPages > 1) "Página $page de $totalPages · $totalCount no total" else "$totalCount no total",
                style = MaterialTheme.typography.labelSmall,
                color = NotifyShareColors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
            )
            if (totalPages > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(totalPages) { i ->
                        val n = i + 1
                        val selected = n == page
                        Text(
                            "$n",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(50),
                                )
                                .clickable { onPage(n) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        footer?.invoke()
    }
}
