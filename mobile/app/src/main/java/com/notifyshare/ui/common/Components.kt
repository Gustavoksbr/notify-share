package com.notifyshare.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notifyshare.ui.theme.NotifyIcons
import com.notifyshare.ui.theme.NotifyShareColors
import kotlin.math.absoluteValue

/**
 * Envolve o conteudo de uma tela abastecida pelo backend com o gesto de
 * "puxar para recarregar". `onRefresh` deve chamar `Connectivity.probeBeforeRefresh()`
 * (feito nos ViewModels) para a barra de "sem internet" aparecer na hora.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(NotifyIcons.Back, "Voltar", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NotifyShareColors.muted,
        modifier = modifier.padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
fun Avatar(name: String, size: Int = 44, modifier: Modifier = Modifier, online: Boolean? = null) {
    val palette = listOf(
        0xFF5B4B8E, 0xFF2E5F52, 0xFF2B4A63, 0xFF7A3B52, 0xFF6B4A2E, 0xFF8E5B2E,
    )
    val color = palette[(name.hashCode().absoluteValue) % palette.size]
    androidx.compose.foundation.layout.Box(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.dp)
                .background(androidx.compose.ui.graphics.Color(color), CircleShape),
        ) {
            Text(
                name.trimStart('@').take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.30f).dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(
                        if (online) NotifyShareColors.online else NotifyShareColors.muted,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Ponto de status solto, para cabecalhos. */
@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(9.dp)
            .background(if (online) NotifyShareColors.online else NotifyShareColors.muted, CircleShape),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = NotifyShareColors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Tentar de novo", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(NotifyShareColors.onlineContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(NotifyShareColors.online, CircleShape),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = NotifyShareColors.online)
    }
}

/**
 * Botao-pilula preenchido (acao primaria de uma linha: "Aceitar", "Adicionar").
 * Enquanto [loading], troca o texto por um spinner do mesmo tamanho — a largura
 * nao pula — e ignora toques. Toda acao que espera resposta do backend usa isto.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val active = enabled && !loading
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                RoundedCornerShape(20.dp),
            )
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * `OutlinedButton` de largura cheia (acoes de perfil: bloquear, oferecer…).
 * Troca o texto por um spinner enquanto [loading].
 */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    textColor: Color = Color.Unspecified,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(text, color = textColor)
        }
    }
}

/**
 * Acao secundaria em texto ("Recusar", "Cancelar", "Remover"). Vira um spinner
 * pequeno enquanto [loading] e para de responder a toques.
 */
@Composable
fun InlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = NotifyShareColors.muted,
) {
    TextButton(onClick = onClick, enabled = !loading, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = color,
            )
        } else {
            Text(text, color = color)
        }
    }
}
