package com.notifyshare.fcm

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessaging
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.ApiResult
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Obtem o token do FCM e registra no servidor. Teimoso de proposito:
 *
 *  - se o Play Services ainda nao emite token (SERVICE_NOT_AVAILABLE, comum e
 *    quase sempre transitorio), volta como retry — o WorkManager tenta de novo
 *    com backoff exponencial, sobrevive a reboot e respeita a constraint de rede
 *  - so desiste depois de ~1 dia de tentativas; ai o proximo `enqueue` (todo
 *    foreground / login) recomeca
 *
 * Sem token, o app continua funcionando: as notificacoes chegam pelo WebSocket
 * com o app aberto e pelo sync do feed ao abrir.
 */
class FcmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NotifyShareApp).container
        val label = "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}".take(80)

        val token = inputData.getString(KEY_TOKEN) ?: fetchToken()
        if (token == null) {
            // Play Services nao cooperou. Continua tentando por um tempo.
            if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
            container.deviceRepository.markFcmBlocked()
            return Result.success()
        }

        return when (container.deviceRepository.registerToken(token, label)) {
            is ApiResult.Ok -> Result.success()
            is ApiResult.Failure -> {
                // offline / 401: o token ja esta salvo como pendente e vai no
                // proximo login ou foreground.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    private suspend fun fetchToken(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val UNIQUE = "fcm-register"
        private const val MAX_ATTEMPTS = 24

        /**
         * Garante o registro. `force` recomeca a cadeia do zero — usado quando o
         * app volta ao primeiro plano e o token ainda nao foi confirmado, para
         * nao ficar preso num backoff longo de uma tentativa antiga.
         */
        fun ensureRegistered(context: Context, force: Boolean = false) {
            enqueue(
                context, token = null,
                policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            )
        }

        /** onNewToken: token novo em maos, registra ja (substitui a cadeia). */
        fun register(context: Context, token: String) {
            enqueue(context, token = token, policy = ExistingWorkPolicy.REPLACE)
        }

        private fun enqueue(context: Context, token: String?, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<FcmSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply { token?.let { setInputData(workDataOf(KEY_TOKEN to it)) } }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, policy, request)
        }
    }
}
