package com.notifyshare.push.adapter

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.notifyshare.push.application.PushMessage
import com.notifyshare.push.application.PushOutcome
import com.notifyshare.push.application.PushPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class FcmPushAdapter(
    // ObjectProvider para o bean poder nao existir (FCM desligado / sem credencial).
    private val messagingProvider: ObjectProvider<FirebaseMessaging>,
) : PushPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(tokens: List<String>, message: PushMessage): Map<String, PushOutcome> {
        val targets = tokens.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty()) return emptyMap()

        val messaging = messagingProvider.ifAvailable ?: run {
            log.debug("push no-op: {} token(s), tipo={}", targets.size, message.type)
            return targets.associateWith { PushOutcome.Failed("fcm_disabled") }
        }

        val payload = message.data + ("type" to message.type)
        return targets.associateWith { token -> sendOne(messaging, token, payload) }
    }

    private fun sendOne(
        messaging: FirebaseMessaging,
        token: String,
        payload: Map<String, String>,
    ): PushOutcome = try {
        val id = messaging.send(
            Message.builder()
                .setToken(token)
                .putAllData(payload)
                .setAndroidConfig(
                    AndroidConfig.builder()
                        // HIGH acorda o app por uma janela curta mesmo em Doze.
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build()
                )
                .build()
        )
        PushOutcome.Delivered(id)
    } catch (e: FirebaseMessagingException) {
        when (e.messagingErrorCode) {
            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> {
                log.info("token do FCM rejeitado, sera removido: {}", e.messagingErrorCode)
                PushOutcome.TokenRejected(token)
            }
            else -> {
                log.warn("falha ao enviar push: {}", e.messagingErrorCode ?: e.message)
                PushOutcome.Failed(e.messagingErrorCode?.name ?: e.message ?: "unknown")
            }
        }
    }
}
