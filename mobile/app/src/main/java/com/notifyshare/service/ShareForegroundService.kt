package com.notifyshare.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notifyshare.MainActivity
import com.notifyshare.NotifyShareApp
import com.notifyshare.R
import com.notifyshare.data.local.DrainState
import com.notifyshare.data.local.PKG_SYSTEM_PHONE
import com.notifyshare.fcm.NotificationChannels
import com.notifyshare.notify.IngestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Servico em primeiro plano ativo enquanto ha compartilhamento:
 *  - notificacao fixa de "servico ativo"
 *  - eventos do "Meu celular" (bateria, carregador, resumo diario)
 *  - WebSocket enquanto o app roda
 *  - agenda o watchdog
 *
 * O evento "celular reiniciou" nao mora aqui — vem do BootReceiver.
 */
class ShareForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var batteryReceiver: BroadcastReceiver? = null

    // Histerese em memoria: dispara uma vez por cruzamento, re-arma ao afastar.
    private var lowArmed = true
    private var highArmed = true

    // Ultima leitura crua de bateria e de "plugado", para confirmar/derivar
    // (ver onBatteryChanged).
    private var lastRawPct: Int? = null
    private var lastPlugged: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0)
        registerBattery()
        WatchdogWorker.schedule(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
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

    private fun registerBattery() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) onBatteryChanged(intent)
            }
        }
        // So ACTION_BATTERY_CHANGED: e sticky (sempre chega, mesmo registrando
        // agora) e carrega tanto o nivel quanto o "plugado" (EXTRA_PLUGGED).
        // Os broadcasts dedicados POWER_CONNECTED/POWER_DISCONNECTED foram
        // removidos daqui de proposito — em alguns fabricantes (MIUI e afins)
        // eles simplesmente nao chegam num receiver registrado em segundo
        // plano, e era por isso que "carregador conectou/desconectou" nunca
        // avisava. O BATTERY_CHANGED e mais confiavel e ja tem o que precisamos.
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        batteryReceiver = receiver
    }

    private fun onBatteryChanged(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        if (plugged >= 0) {
            val nowPlugged = plugged != 0
            val prevPlugged = lastPlugged
            lastPlugged = nowPlugged
            if (prevPlugged != null && prevPlugged != nowPlugged) {
                scope.launch {
                    val app = application as? NotifyShareApp ?: return@launch
                    if (!app.container.systemAlerts.current().charging) return@launch
                    emit(
                        if (nowPlugged) "charge-on" else "charge-off",
                        if (nowPlugged) "Começou a carregar" else "Parou de carregar",
                    )
                }
            }
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val pct = (level * 100) / scale

        // Confirma a leitura antes de usar pra alertas: alguns aparelhos mandam
        // uma leitura de bateria fora do lugar bem no instante em que o
        // carregador conecta/desconecta (foi o que bugou o alerta de bateria
        // baixa — 76% virando "34%" no mesmo minuto). So age quando a leitura
        // nao pula mais que JUMP_LIMIT em relacao a anterior; um salto grande
        // isolado e descartado.
        val prevRaw = lastRawPct
        lastRawPct = pct
        if (prevRaw != null && kotlin.math.abs(pct - prevRaw) > JUMP_LIMIT) return

        scope.launch {
            val app = application as? NotifyShareApp ?: return@launch
            val cfg = app.container.systemAlerts.current()

            if (cfg.batteryLow) {
                if (pct <= cfg.batteryLowPct && lowArmed) {
                    lowArmed = false
                    emit("battery-low", "Bateria em $pct%")
                } else if (pct >= cfg.batteryLowPct + REARM_GAP) {
                    lowArmed = true
                }
            }
            if (cfg.batteryHigh) {
                if (pct >= cfg.batteryHighPct && highArmed) {
                    highArmed = false
                    val msg = if (pct >= 100) "Bateria carregada (100%)" else "Bateria em $pct%"
                    emit("battery-high", msg)
                } else if (pct <= cfg.batteryHighPct - REARM_GAP) {
                    highArmed = true
                }
            }

            updateDailyDrain(app, cfg, pct)
        }
    }

    /** Acumula so as quedas do dia (ignora recarga); no virar do dia, emite o resumo. */
    private suspend fun updateDailyDrain(app: NotifyShareApp, cfg: com.notifyshare.data.local.SystemAlerts, pct: Int) {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val prev = app.container.systemAlerts.drainState()
        if (prev == null) {
            app.container.systemAlerts.setDrainState(DrainState(today, pct, 0))
            return
        }
        if (prev.epochDay != today) {
            if (cfg.dailyDrain && prev.drainAccum > 0) {
                emit("daily-drain", "Ontem o celular consumiu cerca de ${prev.drainAccum}% de bateria")
            }
            app.container.systemAlerts.setDrainState(DrainState(today, pct, 0))
            return
        }
        val drop = (prev.lastLevel - pct).coerceAtLeast(0)
        app.container.systemAlerts.setDrainState(
            DrainState(today, pct, prev.drainAccum + drop),
        )
    }

    /**
     * Cofre local: sempre (se a regra de "Meu celular" capturar). Servidor: so
     * com compartilhamento ativo. dedupKey em janela de 30 min para o "armed"
     * resetado num restart nao virar rajada.
     */
    private fun emit(suffix: String, message: String) {
        val app = application as? NotifyShareApp ?: return
        val now = Instant.now()
        val dedup = "sysphone|$suffix|${now.epochSecond / 1800}"

        val matched = com.notifyshare.notify.VaultMatcher.match(
            rules = app.container.vault.rulesSnapshot(),
            packageName = PKG_SYSTEM_PHONE, eventType = "system", senderHash = null, text = message,
        )
        if (matched != null) {
            scope.launch {
                app.container.vault.addItem(
                    com.notifyshare.data.local.VaultItem(
                        id = dedup,
                        packageName = PKG_SYSTEM_PHONE,
                        eventType = "system",
                        occurredAt = now.toString(),
                        savedAt = now.toString(),
                        body = message,
                    ),
                )
            }
        }

        if (!app.container.sharingActive) return
        IngestWorker.enqueue(
            context = applicationContext,
            packageName = PKG_SYSTEM_PHONE,
            eventType = "system",
            senderHash = null,
            occurredAt = now.toString(),
            dedupKey = dedup,
            content = """{"body":"$message"}""",
        )
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val EXTRA_COUNT = "count"
        private const val REARM_GAP = 5

        /** Leitura de bateria que pula mais que isto de uma vez e descartada
         *  (provavel glitch do sensor, comum ao plugar/desplugar o carregador). */
        private const val JUMP_LIMIT = 12

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
