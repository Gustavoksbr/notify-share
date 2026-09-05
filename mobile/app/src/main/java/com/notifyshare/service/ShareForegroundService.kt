package com.notifyshare.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.notifyshare.MainActivity
import com.notifyshare.NotifyShareApp
import com.notifyshare.R
import com.notifyshare.fcm.NotificationChannels
import com.notifyshare.notify.IngestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Servico em primeiro plano ativo enquanto ha compartilhamento:
 *  - notificacao fixa de "servico ativo"
 *  - eventos de bateria e Wi-Fi
 *  - WebSocket enquanto o app roda
 *  - agenda o watchdog
 */
class ShareForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var powerReceiver: BroadcastReceiver? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0)
        registerPower()
        registerNetwork()
        WatchdogWorker.schedule(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        powerReceiver?.let { runCatching { unregisterReceiver(it) } }
        netCallback?.let { cb -> getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(cb) }
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
        else "Servico ativo"

        val disable = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, ServiceControlReceiver::class.java).setAction(ServiceControlReceiver.ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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

    private fun registerPower() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_LOW -> emit("battery", "bateria baixa", "Bateria baixa")
                    Intent.ACTION_POWER_DISCONNECTED -> emit("battery", "carregador", "Carregador desconectado")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        powerReceiver = receiver
    }

    private fun registerNetwork() {
        val cm = getSystemService<ConnectivityManager>() ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                emit("network", "wifi-lost", "Wi-Fi desconectou")
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    emit("network", "wifi-up", "Wi-Fi conectou")
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        netCallback = callback
    }

    private fun emit(type: String, dedupSuffix: String, message: String) {
        val app = application as? NotifyShareApp ?: return
        if (!app.container.sharingActive) return
        scope.launch {
            val now = Instant.now()
            IngestWorker.enqueue(
                context = applicationContext,
                packageName = if (type == "battery") "system:battery" else "system:wifi",
                eventType = type,
                senderHash = null,
                occurredAt = now.toString(),
                // janela de 10 min: evita repetir o mesmo aviso em rajada
                dedupKey = "$type|$dedupSuffix|${now.epochSecond / 600}",
                content = """{"body":"$message"}""",
            )
        }
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
