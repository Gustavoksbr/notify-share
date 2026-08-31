package com.notifyshare.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icones proprios, tracados, na grade de 24dp.
 *
 * Nao usamos material-icons-extended: aquela dependencia traz milhares de
 * vetores para o app usar dois deles, e o estilo preenchido dela nao combina
 * com o tracado fino do resto da interface.
 *
 * A cor aqui e irrelevante — o composable Icon aplica o tint por cima.
 */
object NotifyIcons {

    val Eye: ImageVector by lazy {
        strokeIcon(
            name = "Eye",
            "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z",
            "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        )
    }

    val EyeOff: ImageVector by lazy {
        strokeIcon(
            name = "EyeOff",
            "M9.9 4.2A10.9 10.9 0 0 1 12 4c6.4 0 10 8 10 8a19.4 19.4 0 0 1-2.6 3.7",
            "M6.6 6.6A19.3 19.3 0 0 0 2 12s3.6 8 10 8a10.9 10.9 0 0 0 4.3-.9",
            "M14.1 14.1a3 3 0 1 1-4.2-4.2",
            "M2 2l20 20",
        )
    }

    private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            pathData.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
