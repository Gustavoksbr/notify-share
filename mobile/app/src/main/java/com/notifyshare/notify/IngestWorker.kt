package com.notifyshare.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.notifyshare.NotifyShareApp
import com.notifyshare.data.ApiResult
import com.notifyshare.data.remote.IngestEventBody
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Envia um evento capturado para o servidor, fora da thread do callback e com
 * retry — se a rede estiver fora, tenta de novo depois. O dedupKey garante que
 * um reenvio nao vira evento duplicado.
 */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NotifyShareApp).container
        val body = IngestEventBody(
            packageName = inputData.getString(K_PKG) ?: return Result.failure(),
            eventType = inputData.getString(K_TYPE) ?: "message",
            senderHash = inputData.getString(K_SENDER),
            occurredAt = inputData.getString(K_AT) ?: Instant.now().toString(),
            dedupKey = inputData.getString(K_DEDUP),
            content = inputData.getString(K_CONTENT),
        )
        return when (container.feedRepository.ingest(body)) {
            is ApiResult.Ok -> Result.success()
            is ApiResult.Failure -> if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val K_PKG = "pkg"
        private const val K_TYPE = "type"
        private const val K_SENDER = "sender"
        private const val K_AT = "at"
        private const val K_DEDUP = "dedup"
        private const val K_CONTENT = "content"

        fun enqueue(
            context: Context,
            packageName: String,
            eventType: String,
            senderHash: String?,
            occurredAt: String,
            dedupKey: String?,
            content: String?,
        ) {
            val request = OneTimeWorkRequestBuilder<IngestWorker>()
                .setInputData(
                    workDataOf(
                        K_PKG to packageName,
                        K_TYPE to eventType,
                        K_SENDER to senderHash,
                        K_AT to occurredAt,
                        K_DEDUP to dedupKey,
                        K_CONTENT to content,
                    ),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
