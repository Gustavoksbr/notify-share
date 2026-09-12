package com.notifyshare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.local.PKG_SYSTEM_PHONE
import com.notifyshare.data.local.VaultItem
import com.notifyshare.notify.IngestWorker
import com.notifyshare.notify.VaultMatcher
import java.time.Instant

/**
 * Depois de reiniciar o aparelho (ou atualizar o app), religa o servico se
 * havia compartilhamento ativo. O trabalho real vai para um Worker porque o
 * onReceive nao pode fazer I/O nem esperar.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<BootWorker>()
                        .setInputData(
                            workDataOf("reboot" to (intent.action == Intent.ACTION_BOOT_COMPLETED)),
                        )
                        .build(),
                )
            }
        }
    }
}

class BootWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NotifyShareApp).container
        val serviceOn = container.serviceSwitch.enabledNow()
        val sharingOn = serviceOn && container.shareState.isActiveNow()
        if (sharingOn || (serviceOn && container.vaultWantsSystemNow())) {
            ShareForegroundService.start(applicationContext)
            WatchdogWorker.schedule(applicationContext)
        }

        // "O celular reiniciou" — so num boot de verdade e se o alerta esta ligado.
        if (inputData.getBoolean("reboot", false) && container.systemAlerts.current().reboot) {
            val now = Instant.now()
            val msg = "O celular reiniciou"
            val dedup = "sysphone|reboot|${now.epochSecond / 300}"

            val matched = VaultMatcher.match(
                container.vault.rulesSnapshot(), PKG_SYSTEM_PHONE, "system", null, msg,
            )
            if (matched != null) {
                container.vault.addItem(
                    VaultItem(
                        id = dedup, packageName = PKG_SYSTEM_PHONE, eventType = "system",
                        occurredAt = now.toString(), savedAt = now.toString(), body = msg,
                    ),
                )
            }
            if (sharingOn) {
                IngestWorker.enqueue(
                    context = applicationContext,
                    packageName = PKG_SYSTEM_PHONE,
                    eventType = "system",
                    senderHash = null,
                    occurredAt = now.toString(),
                    dedupKey = dedup,
                    content = """{"body":"$msg"}""",
                )
            }
        }
        return Result.success()
    }
}
