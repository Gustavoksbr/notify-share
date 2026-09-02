package com.notifyshare.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notifyshare.NotifyShareApp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Le as notificacoes do aparelho. Aqui roda o parsing e a montagem do evento;
 * o filtro fino "Jose sim, Maria nao" fica no servidor (grant_rules), e o gate
 * grosso — "so envia se houver algum compartilhamento meu ativo" — fica aqui.
 *
 * O filtro por remetente sai do MessagingStyle: WhatsApp, Telegram, Signal,
 * Messenger e Discord carregam um objeto Person por mensagem. Nao ha engenharia
 * reversa por app.
 */
class NotificationRelayService : NotificationListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? NotifyShareApp ?: return
        if (sbn.packageName == packageName) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Historico local "apps que te notificaram" — alimenta a tela de escolher
        // apps. Roda sempre, mesmo sem compartilhamento ativo; nada sai do aparelho.
        app.container.appScope.launch {
            runCatching { app.container.recentApps.record(sbn.packageName) }
        }

        if (!app.container.sharingActive) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val plainText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val messaging = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()
        val lastMessage = messaging?.messages?.lastOrNull()

        val sender = lastMessage?.person?.name?.toString() ?: title
        val body = lastMessage?.text?.toString() ?: plainText ?: return
        if (body.isBlank()) return

        val occurredMillis = lastMessage?.timestamp ?: sbn.postTime
        val senderHash = sender?.takeIf { it.isNotBlank() }?.let(::hashSender)
        val dedupKey = "${sbn.packageName}|${senderHash ?: "-"}|$occurredMillis"

        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull()

        val content = buildJsonObject {
            appLabel?.let { put("appLabel", JsonPrimitive(it)) }
            title?.let { put("title", JsonPrimitive(it)) }
            put("body", JsonPrimitive(body))
            sender?.let { put("sender", JsonPrimitive(it)) }
        }

        IngestWorker.enqueue(
            context = applicationContext,
            packageName = sbn.packageName,
            eventType = "message",
            senderHash = senderHash,
            occurredAt = Instant.ofEpochMilli(occurredMillis).toString(),
            dedupKey = dedupKey,
            content = (content as JsonObject).toString(),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
