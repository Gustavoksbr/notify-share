package com.notifyshare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notifyshare.NotifyShareApp

/**
 * Depois de reiniciar o aparelho (ou atualizar o app), religa o servico se
 * havia compartilhamento ativo. O trabalho real vai para um Worker porque o
 * onReceive nao pode fazer I/O nem esperar.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<BootWorker>().build())
            }
        }
    }
}

class BootWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NotifyShareApp).container
        if (container.serviceSwitch.enabledNow() && container.shareState.isActiveNow()) {
            ShareForegroundService.start(applicationContext)
            WatchdogWorker.schedule(applicationContext)
        }
        return Result.success()
    }
}
