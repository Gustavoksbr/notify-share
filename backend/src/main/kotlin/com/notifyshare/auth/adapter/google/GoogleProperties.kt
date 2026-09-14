package com.notifyshare.auth.adapter.google

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notifyshare.google")
data class GoogleProperties(
    /**
     * Client ID do tipo "Web application" do Google Cloud. E o mesmo valor que o
     * app Android usa como serverClientId no Credential Manager. Vazio = login
     * com Google desligado.
     */
    val clientId: String = "",
    /**
     * Client ID do tipo "Android". So esse tipo de client aceita redirect com
     * esquema customizado (com.notifyshare:/...) — por isso o fallback de login
     * pelo navegador (quando o Credential Manager nao acha conta no aparelho)
     * usa este client em vez do Web, e o ID token que volta traz ESTE como
     * audience. Opcional: sem ele, so o fluxo por Credential Manager funciona.
     */
    val androidClientId: String = "",
) {
    val enabled: Boolean get() = clientId.isNotBlank()

    /** Audiences aceitos num ID token do Google — o do Credential Manager e,
     *  se configurado, o do fallback pelo navegador. */
    val acceptedAudiences: Set<String> get() = setOfNotNull(clientId, androidClientId.takeIf { it.isNotBlank() })
}
