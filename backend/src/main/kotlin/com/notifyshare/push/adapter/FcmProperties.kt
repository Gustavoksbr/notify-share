package com.notifyshare.push.adapter

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notifyshare.fcm")
data class FcmProperties(
    val enabled: Boolean = true,
    /** Caminho de um arquivo JSON de conta de servico. Usado quando o JSON inline esta vazio. */
    val credentialsPath: String = "firebase-service-account.json",
    /** Conteudo do JSON da conta de servico. Preferido em producao (vem de secret, sem arquivo). */
    val credentialsJson: String? = null,
)
