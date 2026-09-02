package com.notifyshare.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.notifyshare.core.AppEvents

/**
 * Recebe o push do FCM. As mensagens sao sempre data-only: montamos a
 * notificacao aqui (NotificationPublisher) e sinalizamos as telas vivas para
 * recarregarem (AppEvents).
 */
class NotifyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmSyncWorker.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "event" -> {
                NotificationPublisher(this).publishEvent(data)
                AppEvents.signal(AppEvents.FEED)
            }
            "message" -> {
                NotificationPublisher(this).publishMessage(data)
                AppEvents.signal(AppEvents.CHAT)
            }
            "message_read", "typing" -> AppEvents.signal(AppEvents.CHAT)
            "grant" -> {
                AppEvents.signal(AppEvents.GRANTS)
                AppEvents.signal(AppEvents.FRIENDS)
            }
            "presence" -> AppEvents.signal(AppEvents.PRESENCE)
        }
    }
}
