package com.notifyshare.auth.adapter.google

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notifyshare.google")
data class GoogleProperties(
    /**
     * Client ID do tipo "Web application" do Google Cloud. E o mesmo valor que o
     * app Android usa como serverClientId. Vazio = login com Google desligado.
     */
    val clientId: String = "",
) {
    val enabled: Boolean get() = clientId.isNotBlank()
}
