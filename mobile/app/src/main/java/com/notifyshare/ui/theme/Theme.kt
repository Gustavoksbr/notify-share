package com.notifyshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tema unico e escuro, com os tokens que saíram dos mockups.
 *
 * O app so tem tema escuro por enquanto, mas nenhuma cor literal deve aparecer
 * fora deste arquivo. Sempre MaterialTheme.colorScheme.algumaCoisa nas telas.
 * Essa e a regra que faz "adicionar tema claro" virar acrescentar um
 * lightColorScheme aqui, em vez de uma varredura em todas as telas.
 *
 * Ver ADR sobre tema em DECISOES.md.
 */
private val NotifyShareDarkColors = darkColorScheme(
    primary = Color(0xFFFFB868),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDCBE),

    secondary = Color(0xFFA8C7FA),
    onSecondary = Color(0xFF0A305F),

    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),

    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2E2F33),
    onSurfaceVariant = Color(0xFFA9A8B3),

    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1A1B1F),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF292A2E),
    surfaceContainerHighest = Color(0xFF34353A),

    outline = Color(0xFF45464F),
    outlineVariant = Color(0xFF2E2F33),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Verde de "compartilhando agora" e ambar de "offline". Nao existem no Material 3. */
object NotifyShareColors {
    val online = Color(0xFF7ED9A5)
    val onlineContainer = Color(0xFF16241C)
    val onlineOutline = Color(0xFF2C4A38)
    val warning = Color(0xFFFFB868)
    val warningContainer = Color(0xFF2B2113)
    val warningOutline = Color(0xFF4A3A1E)
    val muted = Color(0xFF8E8D97)
}

private val NotifyShareTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun NotifyShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NotifyShareDarkColors,
        typography = NotifyShareTypography,
        content = content,
    )
}
