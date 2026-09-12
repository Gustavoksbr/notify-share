package com.notifyshare.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Barra de rolagem fina e sempre visível quando o conteúdo transborda — deixa
 * explícito que há mais coisa abaixo (ex.: o botão do Google na tela de login).
 * Sem interação; é só um indicador.
 */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 3.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier = composed {
    val canScroll = state.maxValue > 0
    val alpha by animateFloatAsState(if (canScroll) 1f else 0f, label = "scrollbarAlpha")

    drawWithContent {
        drawContent()
        if (alpha <= 0f || state.maxValue == Int.MAX_VALUE) return@drawWithContent

        val viewport = this.size.height
        val total = viewport + state.maxValue
        val barHeight = (viewport / total * viewport).coerceAtLeast(24.dp.toPx())
        val maxOffset = viewport - barHeight
        val barOffset = (state.value.toFloat() / state.maxValue) * maxOffset
        val w = width.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width - w, barOffset),
            size = Size(w, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2),
            alpha = alpha,
        )
    }
}
