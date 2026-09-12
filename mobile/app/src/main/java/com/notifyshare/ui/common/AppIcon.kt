package com.notifyshare.ui.common

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.notifyshare.ui.theme.NotifyIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sentinela: pacote ja consultado e sem icone; nao consulta de novo. */
private val EMPTY: ImageBitmap = ImageBitmap(1, 1)
private val iconCache = LruCache<String, ImageBitmap>(120)

/**
 * Icone de um app pelo nome do pacote, carregado do PackageManager (IO) e
 * guardado num cache pequeno em memoria — as listas repetem os mesmos pacotes.
 * Pacotes sinteticos ("system:battery"/"system:wifi") e apps ausentes deste
 * aparelho caem num icone tracado.
 */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    when (packageName) {
        "system:phone" -> return SyntheticIcon(NotifyIcons.Battery, size, modifier)
        "system:battery" -> return SyntheticIcon(NotifyIcons.Battery, size, modifier)
        "system:wifi" -> return SyntheticIcon(NotifyIcons.Wifi, size, modifier)
    }

    val bitmap by produceState<ImageBitmap?>(iconCache.get(packageName), packageName) {
        val cached = iconCache.get(packageName)
        value = cached ?: withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }

    val bmp = bitmap
    if (bmp == null || bmp === EMPTY) {
        SyntheticIcon(NotifyIcons.AppFallback, size, modifier)
    } else {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)),
        )
    }
}

@Composable
private fun SyntheticIcon(icon: ImageVector, size: Dp, modifier: Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap {
    val result = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val px = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        drawable.toBitmap(px, px).asImageBitmap()
    }.getOrDefault(EMPTY)
    iconCache.put(packageName, result)
    return result
}
