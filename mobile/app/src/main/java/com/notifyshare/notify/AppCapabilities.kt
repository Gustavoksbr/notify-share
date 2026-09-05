package com.notifyshare.notify

/**
 * O que um app especifico permite refinar, alem do generico que TODO app
 * suporta via NotificationListenerService: ligar/desligar e conteudo/so aviso.
 *
 * De proposito NAO tentamos adivinhar por uma API do Android se um app "tem
 * remetentes" — isso sugeriria escolher remetente em apps que nunca tem essa
 * nocao (um app de banco, um jogo), o que confunde mais do que ajuda. Cada app
 * suportado entra aqui, um por um; o resto fica no generico.
 *
 * `CONTACTS`: o app usa numero/nome da AGENDA do aparelho como remetente
 * (WhatsApp) — o seletor mostra a lista de contatos com o telefone do lado, em
 * vez de pedir para digitar (ninguem confia em digitar um nome de cabeca).
 *
 * `FREE_TEXT`: o app tambem carrega um Person por mensagem (MessagingStyle),
 * mas o nome não vem de uma agenda local previsivel — digitar o nome como
 * aparece na notificacao funciona.
 */
enum class SenderPickerType { NONE, CONTACTS, FREE_TEXT }

data class AppCapability(val senderPicker: SenderPickerType = SenderPickerType.NONE) {
    val supportsSenders: Boolean get() = senderPicker != SenderPickerType.NONE
}

object AppCapabilities {

    private val KNOWN: Map<String, AppCapability> = mapOf(
        "com.whatsapp" to AppCapability(SenderPickerType.CONTACTS),
        "com.whatsapp.w4b" to AppCapability(SenderPickerType.CONTACTS),
        "org.telegram.messenger" to AppCapability(SenderPickerType.FREE_TEXT),
        "org.thoughtcrime.securesms" to AppCapability(SenderPickerType.FREE_TEXT), // Signal
        "com.facebook.orca" to AppCapability(SenderPickerType.FREE_TEXT), // Messenger
    )

    /** Sem entrada = so o generico (liga/desliga, conteudo/so aviso). */
    fun of(packageName: String): AppCapability = KNOWN[packageName] ?: AppCapability()
}
