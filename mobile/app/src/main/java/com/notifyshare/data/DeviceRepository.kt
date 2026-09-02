package com.notifyshare.data

import com.notifyshare.data.local.SecureTokenStore
import com.notifyshare.data.remote.NotifyShareApi
import com.notifyshare.data.remote.RegisterDeviceBody
import com.notifyshare.data.remote.UnregisterDeviceBody
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Token do FCM e presenca. O token so e util com uma sessao; se ainda nao ha,
 * guardamos como pendente e o [flushPendingToken] reenvia apos o login.
 */
class DeviceRepository(
    private val api: NotifyShareApi,
    private val json: Json,
    private val tokenStore: SecureTokenStore,
) {

    /** ok | blocked | null (tentando) — para a tela de Permissoes mostrar. */
    val fcmStatus: Flow<String?> = tokenStore.fcmStatus

    suspend fun registerToken(fcmToken: String, deviceLabel: String?): ApiResult<Unit> {
        tokenStore.savePendingFcmToken(fcmToken)
        val result = apiCall(json) {
            api.registerDevice(RegisterDeviceBody(fcmToken, "android", deviceLabel))
        }
        if (result is ApiResult.Ok) {
            tokenStore.clearPendingFcmToken(fcmToken)
            tokenStore.setFcmStatus("ok")
        }
        return result
    }

    /** Chamado apos o login: envia o token que ficou pendente, se houver. */
    suspend fun flushPendingToken(deviceLabel: String?) {
        val pending = tokenStore.pendingFcmToken() ?: return
        registerToken(pending, deviceLabel)
    }

    suspend fun markFcmBlocked() = tokenStore.setFcmStatus("blocked")

    suspend fun unregisterToken(fcmToken: String): ApiResult<Unit> =
        apiCall(json) { api.unregisterDevice(UnregisterDeviceBody(fcmToken)) }

    suspend fun heartbeat(): ApiResult<Unit> = apiCall(json) { api.heartbeat() }
}
