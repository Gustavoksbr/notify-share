package com.notifyshare.core

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * O acesso a notificacoes (NotificationListenerService) e o que faz o
 * compartilhamento de apps funcionar. E uma permissao especial, concedida a mao
 * nas Configuracoes, e o Android a REVOGA quando o app e reinstalado ou trocado
 * — entao vale checar sempre, nao so no onboarding.
 *
 * Sem ela, eventos de bateria/Wi-Fi ainda saem (vem de um BroadcastReceiver),
 * mas nenhuma notificacao de app e capturada. Foi exatamente isso que fez o
 * compartilhamento "nao funcionar" apos instalar o build de producao.
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any { it.substringBefore("/") == context.packageName }
    }

    /** Tela do sistema para ligar/desligar o acesso. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
