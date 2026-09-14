package com.notifyshare.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notifyshare.MainActivity
import com.notifyshare.R
import com.notifyshare.fcm.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Servico em primeiro plano ativo enquanto ha compartilhamento:
 *  - notificacao fixa de "servico ativo"
 *  - WebSocket enquanto o app roda
 *  - agenda o watchdog
 */
class ShareForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0)
        WatchdogWorker.schedule(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(count: Int) {
        NotificationChannels.ensure(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count > 0) "Compartilhando com $count " + (if (count == 1) "pessoa" else "pessoas")
        else "Serviço ativo"

        val disable = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, ServiceControlReceiver::class.java).setAction(ServiceControlReceiver.ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notify Share")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_lock_power_off, "Desligar", disable)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val EXTRA_COUNT = "count"

        fun start(context: Context, peopleCount: Int = 0) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ShareForegroundService::class.java).putExtra(EXTRA_COUNT, peopleCount),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShareForegroundService::class.java))
        }
    }
}
