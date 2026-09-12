package com.notifyshare.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.local.VaultItem
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Le as notificacoes do aparelho. Aqui roda o parsing e a montagem do evento.
 *
 * Dois destinos, independentes:
 *  - compartilhamento: so quando ha algum grant ativo (gate `sharingActive`).
 *    O filtro fino "Jose sim, Maria nao" fica no servidor (grant_rules).
 *  - cofre local: sempre — sem login, sem compartilhamento. O filtro roda aqui
 *    (VaultMatcher) contra as regras do VaultStore.
 *
 * O filtro por remetente sai do MessagingStyle: WhatsApp, Telegram, Signal,
 * Messenger e Discord carregam um objeto Person por mensagem.
 */
class NotificationRelayService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as? NotifyShareApp ?: return
        if (sbn.packageName == packageName) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Historico local "apps que te notificaram" — alimenta a tela de escolher
        // apps. Roda sempre, nada sai do aparelho.
        app.container.appScope.launch {
            runCatching { app.container.recentApps.record(sbn.packageName) }
        }

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val plainText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val messaging = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()
        val lastMessage = messaging?.messages?.lastOrNull()

        val groupName = messaging?.conversationTitle?.toString()?.takeIf { gt ->
            gt.isNotBlank() && (messaging.isGroupConversation || gt != lastMessage?.person?.name?.toString())
        }

        val sender = lastMessage?.person?.name?.toString() ?: title
        val body = lastMessage?.text?.toString() ?: plainText ?: return
        if (body.isBlank()) return

        val occurredMillis = lastMessage?.timestamp ?: sbn.postTime
        val occurredAt = Instant.ofEpochMilli(occurredMillis).toString()
        val senderHash = sender?.takeIf { it.isNotBlank() }?.let(::hashSender)
        val dedupKey = "${sbn.packageName}|${senderHash ?: "-"}|$occurredMillis"

        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull()

        // --- cofre local (offline, sem login) --------------------------------
        val matched = VaultMatcher.match(
            rules = app.container.vault.rulesSnapshot(),
            packageName = sbn.packageName,
            eventType = "message",
            senderHash = senderHash,
            text = "${title.orEmpty()} $body",
        )
        if (matched != null) {
            val senderOnly = matched.contentMode == "sender_only"
            app.container.appScope.launch {
                app.container.vault.addItem(
                    VaultItem(
                        id = dedupKey,
                        packageName = sbn.packageName,
                        eventType = "message",
                        occurredAt = occurredAt,
                        savedAt = Instant.now().toString(),
                        title = if (senderOnly) null else title,
                        body = if (senderOnly) null else body,
                        sender = sender,
                        group = groupName,
                        mode = if (senderOnly) "sender_only" else "content",
                    ),
                )
            }
        }

        // --- compartilhamento (so com grant ativo) --------------------------
        if (!app.container.sharingActive) return

        val content = buildJsonObject {
            appLabel?.let { put("appLabel", JsonPrimitive(it)) }
            title?.let { put("title", JsonPrimitive(it)) }
            put("body", JsonPrimitive(body))
            sender?.let { put("sender", JsonPrimitive(it)) }
            groupName?.let { put("group", JsonPrimitive(it)) }
        }

        IngestWorker.enqueue(
            context = applicationContext,
            packageName = sbn.packageName,
            eventType = "message",
            senderHash = senderHash,
            occurredAt = occurredAt,
            dedupKey = dedupKey,
            content = (content as JsonObject).toString(),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
