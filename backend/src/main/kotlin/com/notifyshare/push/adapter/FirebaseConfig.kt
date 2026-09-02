package com.notifyshare.push.adapter

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

/**
 * Inicializa o Firebase Admin SDK se houver credencial. Sem credencial — ou com
 * `notifyshare.fcm.enabled=false` — o bean simplesmente nao existe, e o
 * [FcmPushAdapter] degrada para no-op em vez de derrubar a aplicacao.
 */
@Configuration
class FirebaseConfig(private val props: FcmProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun firebaseMessaging(): FirebaseMessaging? {
        if (!props.enabled) {
            log.info("notifyshare.fcm.enabled=false — push desabilitado")
            return null
        }
        val credentials = loadCredentials() ?: run {
            log.warn(
                "Credencial do FCM ausente (JSON inline vazio e arquivo '{}' inexistente) — push vira no-op",
                props.credentialsPath,
            )
            return null
        }

        val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build()
            )
        log.info("FCM inicializado")
        return FirebaseMessaging.getInstance(app)
    }

    private fun loadCredentials(): GoogleCredentials? {
        props.credentialsJson?.takeIf { it.isNotBlank() }?.let {
            return GoogleCredentials.fromStream(it.byteInputStream())
        }
        val file = File(props.credentialsPath)
        return if (file.exists()) file.inputStream().use(GoogleCredentials::fromStream) else null
    }
}
