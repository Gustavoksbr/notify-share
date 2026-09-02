package com.notifyshare.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Canais de notificacao. Criados uma vez no start do app.
 *
 *  - events   : as notificacoes compartilhadas (o produto)
 *  - messages : a conversa
 *  - service  : a notificacao fixa de "servico ativo" (silenciosa)
 *  - alerts   : avisos do proprio app ("seu aparelho ficou offline")
 */
object NotificationChannels {

    const val EVENTS = "events"
    const val MESSAGES = "messages"
    const val SERVICE = "service"
    const val ALERTS = "alerts"

    fun ensure(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(EVENTS, "Notificacoes compartilhadas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "O que as pessoas escolheram compartilhar com voce"
                },
                NotificationChannel(MESSAGES, "Mensagens", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Conversas dentro do Notify Share"
                },
                NotificationChannel(SERVICE, "Servico ativo", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Aviso fixo enquanto ha compartilhamento ativo"
                    setShowBadge(false)
                },
                NotificationChannel(ALERTS, "Avisos do app", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Quando algo precisa da sua atencao"
                },
            )
        )
    }
}
