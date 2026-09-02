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

    val Bell: ImageVector by lazy {
        strokeIcon(
            name = "Bell",
            "M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8z",
            "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        )
    }

    val Users: ImageVector by lazy {
        strokeIcon(
            name = "Users",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z",
            "M22 21v-2a4 4 0 0 0-3-3.9",
            "M16 3.1a4 4 0 0 1 0 7.8",
        )
    }

    val Share: ImageVector by lazy {
        strokeIcon(
            name = "Share",
            "M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
            "M8.6 13.5l6.8 4",
            "M15.4 6.5l-6.8 4",
        )
    }

    val User: ImageVector by lazy {
        strokeIcon(
            name = "User",
            "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2",
            "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z",
        )
    }

    val Back: ImageVector by lazy {
        strokeIcon(name = "Back", "M19 12H5", "M12 19l-7-7 7-7")
    }

    val Search: ImageVector by lazy {
        strokeIcon(name = "Search", "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M21 21l-4.3-4.3")
    }

    val Send: ImageVector by lazy {
        strokeIcon(name = "Send", "M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z")
    }

    val Check: ImageVector by lazy {
        strokeIcon(name = "Check", "M20 6L9 17l-5-5")
    }

    val Plus: ImageVector by lazy {
        strokeIcon(name = "Plus", "M12 5v14", "M5 12h14")
    }

    val Close: ImageVector by lazy {
        strokeIcon(name = "Close", "M18 6L6 18", "M6 6l12 12")
    }

    val Chevron: ImageVector by lazy {
        strokeIcon(name = "Chevron", "M9 18l6-6-6-6")
    }

    val Battery: ImageVector by lazy {
        strokeIcon(
            name = "Battery",
            "M3 8a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M21 10v4",
        )
    }

    val Wifi: ImageVector by lazy {
        strokeIcon(
            name = "Wifi",
            "M2 8.5a16 16 0 0 1 20 0",
            "M5 12a11 11 0 0 1 14 0",
            "M8.5 15.5a6 6 0 0 1 7 0",
            "M12 19h.01",
        )
    }

    val AppFallback: ImageVector by lazy {
        strokeIcon(
            name = "AppFallback",
            "M4 5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z",
            "M8 8h8v8H8z",
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
