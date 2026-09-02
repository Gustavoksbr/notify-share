package com.notifyshare.core

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.notifyshare.data.remote.RealtimeClient
import com.notifyshare.fcm.FcmSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Segue o primeiro plano do app inteiro:
 *  - liga/desliga o WebSocket (isto e o que define "online" para os amigos)
 *  - ao voltar para o primeiro plano, ressincroniza o compartilhamento e
 *    recomeca a busca do token do FCM se ela tinha desistido
 *  - enquanto o app esta aberto, faz um ping leve no servidor a cada 20s
 *    (estilo rede social) para a barra "servidor fora do ar" aparecer mesmo
 *    sem o usuario ter feito nenhuma acao
 *
 * Registrado no onCreate da Application com ProcessLifecycleOwner.
 */
class AppLifecycleObserver(
    private val appContext: Context,
    private val realtime: RealtimeClient,
    private val onForeground: () -> Unit,
    private val fcmRegistered: () -> Boolean,
    private val scope: CoroutineScope,
    private val healthCheck: suspend () -> Unit,
) : DefaultLifecycleObserver {

    private var pollJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        realtime.start()
        onForeground()
        FcmSyncWorker.ensureRegistered(appContext, force = !fcmRegistered())

        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                runCatching { healthCheck() }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        realtime.stop()
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
    }
}
