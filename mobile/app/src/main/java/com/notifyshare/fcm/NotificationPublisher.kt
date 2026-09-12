package com.notifyshare.fcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.notifyshare.MainActivity
import com.notifyshare.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Monta a notificacao visivel a partir do payload de dados do FCM. O servidor
 * manda so metadados + um `content` opaco; e aqui que ele vira uma notificacao
 * no formato do card do Feed.
 */
class NotificationPublisher(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val manager = NotificationManagerCompat.from(context)

    fun publishEvent(data: Map<String, String>) {
        val deliveryId = data["deliveryId"] ?: return
        val from = data["origin"].orEmpty()
        val pkg = data["packageName"].orEmpty()
        val mode = data["mode"] ?: "content"
        val eventType = data["eventType"] ?: "message"
        val content = data["content"]?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

        val appLabel = content?.str("appLabel")
            ?: content?.str("title")
            ?: friendlyPackage(pkg)
        val sender = content?.str("sender")

        val title = when {
            eventType == "battery" -> "Bateria"
            eventType == "network" -> "Wi-Fi"
            sender != null -> "$appLabel · $sender"
            else -> appLabel
        }
        val body = when (mode) {
            "sender_only" -> "Conteudo oculto — so o remetente"
            else -> content?.str("body") ?: "Nova notificacao"
        }

        notify(
            channel = NotificationChannels.EVENTS,
            id = deliveryId.hashCode(),
            title = title,
            body = body,
            subText = if (from.isNotBlank()) "via @$from" else null,
            open = "feed",
        )
    }

    fun publishMessage(data: Map<String, String>) {
        val from = data["sender"].orEmpty()
        val preview = data["preview"].orEmpty()
        notify(
            channel = NotificationChannels.MESSAGES,
            id = ("msg:$from").hashCode(),
            title = if (from.isNotBlank()) "@$from" else "Mensagem",
            body = preview.ifBlank { "Nova mensagem" },
            subText = null,
            open = "chat:$from",
        )
    }

    private fun notify(
        channel: String,
        id: Int,
        title: String,
        body: String,
        subText: String?,
        open: String,
    ) {
        NotificationChannels.ensure(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN, open)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .apply { subText?.let { setSubText(it) } }
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        if (hasPostPermission()) {
            runCatching { manager.notify(id, notification) }
        }
    }

    private fun hasPostPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun friendlyPackage(pkg: String): String = when {
        pkg == "system:phone" -> "Meu celular"
        pkg.startsWith("system:") -> pkg.removePrefix("system:").replaceFirstChar(Char::uppercase)
        else -> runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrElse { pkg.substringAfterLast('.').replaceFirstChar(Char::uppercase) }
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
}
