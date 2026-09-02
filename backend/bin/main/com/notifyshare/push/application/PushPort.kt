package com.notifyshare.push.application

/**
 * Porta de saida para notificacao push. A implementacao datada e o FCM
 * (ver ADR sobre transporte). Trocar por outra (APNs, WebPush) mexe so no adapter.
 *
 * As mensagens sao sempre data-only: o payload nao carrega o bloco `notification`
 * do FCM. Quem monta a notificacao visivel e o app, para ter controle total do
 * layout, da deduplicacao e do estado "conteudo oculto".
 */
data class PushMessage(
    /** event | message | grant | presence | system — o app roteia por aqui. */
    val type: String,
    /** Pares extras do payload. `type` e injetado automaticamente. */
    val data: Map<String, String> = emptyMap(),
)

sealed interface PushOutcome {
    data class Delivered(val messageId: String) : PushOutcome
    /** Token morto (app desinstalado, token trocado). Quem chamou deve apaga-lo. */
    data class TokenRejected(val token: String) : PushOutcome
    data class Failed(val reason: String) : PushOutcome
}

interface PushPort {

    /** Envia a mesma mensagem para cada token e devolve o resultado de cada um. */
    fun send(tokens: List<String>, message: PushMessage): Map<String, PushOutcome>
}
