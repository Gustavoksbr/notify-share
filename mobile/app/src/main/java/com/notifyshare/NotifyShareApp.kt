package com.notifyshare

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.notifyshare.core.AppLifecycleObserver
import com.notifyshare.data.AuthRepository
import com.notifyshare.data.ChatRepository
import com.notifyshare.data.DeviceRepository
import com.notifyshare.data.FeedRepository
import com.notifyshare.data.SocialRepository
import com.notifyshare.data.ApiResult
import com.notifyshare.data.local.JsonCache
import com.notifyshare.data.local.SecureTokenStore
import com.notifyshare.data.local.ShareState
import com.notifyshare.data.remote.NetworkModule
import com.notifyshare.data.remote.RealtimeClient
import com.notifyshare.fcm.NotificationChannels
import com.notifyshare.fcm.FcmSyncWorker
import com.notifyshare.service.ShareForegroundService
import com.notifyshare.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contagem de pedidos pendentes que precisam de uma acao (aceitar/recusar),
 * para os badges dos icones de baixo. So o "incoming" entra aqui: o que o
 * usuario esta esperando de resposta (outgoing) nao pede nada dele agora.
 */
data class RequestBadges(
    val incomingShareRequests: Int = 0,
    val incomingFriendRequests: Int = 0,
)

/**
 * Container de dependencias feito a mao. O grafo cresceu com as features novas
 * (amigos, grants, eventos, conversa, aparelhos), mas ainda cabe aqui — a
 * migracao para Hilt, se vier, mexe so neste arquivo e nos factories.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob())

    private val tokenStore = SecureTokenStore(context.applicationContext)
    private val api = NetworkModule.api(tokenStore)
    private val json = NetworkModule.json

    val session = SessionState()
    val shareState = ShareState(context.applicationContext)
    val serviceSwitch = com.notifyshare.data.local.ServiceSwitch(context.applicationContext)
    val cache = JsonCache(context.applicationContext) { tokenStore.activeId() }
    val recentApps = com.notifyshare.data.local.RecentAppsStore(context.applicationContext)

    val authRepository = AuthRepository(tokenStore, api, json)
    val socialRepository = SocialRepository(api, json, cache)
    val feedRepository = FeedRepository(api, json, cache)
    val chatRepository = ChatRepository(api, json, cache)
    val deviceRepository = DeviceRepository(api, json, tokenStore)

    val realtime = RealtimeClient(tokenStore, appScope)

    /** ok | blocked | null — estado da entrega em segundo plano (FCM). */
    val fcmStatus = deviceRepository.fcmStatus

    @Volatile
    var fcmRegistered: Boolean = false
        private set

    /** Espelho sincrono de shareState.hasActiveShare, para o NotificationListener
     *  decidir sem chamada suspend na thread do callback. */
    @Volatile
    var sharingActive: Boolean = false
        private set

    private val _requestBadges = MutableStateFlow(RequestBadges())
    val requestBadges: StateFlow<RequestBadges> = _requestBadges.asStateFlow()

    /** Chamado ao abrir o app e a cada evento de grant/amizade. Mantem o ultimo
     *  valor conhecido se uma das duas chamadas falhar, em vez de zerar o badge. */
    suspend fun refreshRequestBadges() {
        val grants = socialRepository.pendingGrants()
        val friends = socialRepository.friendRequests()
        _requestBadges.value = RequestBadges(
            incomingShareRequests = (grants as? ApiResult.Ok)?.value?.incoming?.size
                ?: _requestBadges.value.incomingShareRequests,
            incomingFriendRequests = (friends as? ApiResult.Ok)?.value?.incoming?.size
                ?: _requestBadges.value.incomingFriendRequests,
        )
    }

    init {
        appScope.launch {
            shareState.hasActiveShare.collect { sharingActive = it }
        }
        appScope.launch {
            fcmStatus.collect { fcmRegistered = it == "ok" }
        }
    }
}

class NotifyShareApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.notifyshare.core.NetworkMonitor.init(this)
        NotificationChannels.ensure(this)
        FcmSyncWorker.ensureRegistered(this)
        // WebSocket segue o primeiro plano do app inteiro (= presenca "online").
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(
                this, container.realtime,
                onForeground = ::refreshSharing,
                fcmRegistered = { container.fcmRegistered },
                scope = container.appScope,
                healthCheck = { container.authRepository.ping() },
            ),
        )
    }

    /**
     * Sincroniza o estado local de compartilhamento com o servidor e liga ou
     * desliga o servico em primeiro plano. Chamado quando o app abre e quando
     * chega um evento de grant.
     */
    /**
     * Troca a conta ativa: o refresh token guardado ainda vale, entao nao ha
     * reautenticacao. Limpa o cache da conta antiga, reinicia WebSocket e FCM,
     * e recria a Activity para as telas relerem tudo do zero.
     */
    fun switchAccount(id: String, thenOnMain: () -> Unit) {
        container.appScope.launch {
            container.authRepository.switchAccount(id)
            container.cache.clear()
            container.session.clear()
            container.realtime.stop()
            container.realtime.start()
            FcmSyncWorker.ensureRegistered(this@NotifyShareApp, force = true)
            refreshSharing()
            withContext(Dispatchers.Main) { thenOnMain() }
        }
    }

    fun refreshSharing() {
        container.appScope.launch {
            // interruptor mestre: desligado = servico parado, sem mais nada
            if (!container.serviceSwitch.enabledNow()) {
                runCatching { ShareForegroundService.stop(this@NotifyShareApp) }
                container.shareState.update(false, 0)
                return@launch
            }
            val grants = container.socialRepository.grants("sharer")
            val active = (grants as? ApiResult.Ok)?.value?.count { it.isActive } ?: return@launch
            container.shareState.update(active > 0, active)
            // start() pode ser bloqueado se o app estiver em segundo plano; nesse
            // caso o proximo retorno ao primeiro plano tenta de novo.
            runCatching {
                if (active > 0) ShareForegroundService.start(this@NotifyShareApp, active)
                else ShareForegroundService.stop(this@NotifyShareApp)
            }
        }
    }
}
