package com.notifyshare.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notifyshare.NotifyShareApp
import com.notifyshare.fcm.FcmSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Reafirma o servico em primeiro plano e manda um heartbeat de presenca. Roda a
 * cada 15 min (piso do WorkManager) enquanto ha compartilhamento ativo — e a
 * rede de seguranca contra OEMs que matam servico.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NotifyShareApp).container
        if (!container.shareState.isActiveNow()) {
            cancel(applicationContext)
            return Result.success()
        }
        ShareForegroundService.start(applicationContext)
        container.deviceRepository.heartbeat()
        // reafirma o token do FCM: se o servidor tiver descartado (push falhou),
        // isto o recadastra sem esperar o app voltar ao primeiro plano
        FcmSyncWorker.ensureRegistered(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "notifyshare-watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
