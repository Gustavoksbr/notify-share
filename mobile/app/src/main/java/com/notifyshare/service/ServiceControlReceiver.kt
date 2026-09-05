package com.notifyshare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notifyshare.MainActivity
import com.notifyshare.NotifyShareApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recebe o toque em "Desligar" na notificacao fixa. Desliga o interruptor
 * mestre, para o servico e leva o usuario para Permissoes, onde ele pode
 * religar. O app continua funcionando quando aberto.
 */
class ServiceControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISABLE) return
        val app = context.applicationContext as? NotifyShareApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.serviceSwitch.set(false)
                ShareForegroundService.stop(context)
            } finally {
                pending.finish()
            }
        }
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, "permissions")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        const val ACTION_DISABLE = "com.notifyshare.action.DISABLE_SERVICE"
    }
}
