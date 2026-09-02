package com.notifyshare.data

import android.os.Build
import com.notifyshare.data.local.SecureTokenStore
import com.notifyshare.data.remote.GoogleLoginRequest
import com.notifyshare.data.remote.LoginRequest
import com.notifyshare.data.remote.LogoutRequest
import com.notifyshare.data.remote.NetworkModule
import com.notifyshare.data.remote.NotifyShareApi
import com.notifyshare.data.remote.RegisterRequest
import com.notifyshare.data.remote.TokenResponse
import com.notifyshare.data.remote.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class AuthRepository(
    private val tokenStore: SecureTokenStore,
    private val api: NotifyShareApi = NetworkModule.api(tokenStore),
    private val json: Json = NetworkModule.json,
) {

    val hasSession: Flow<Boolean> = tokenStore.hasSession

    private val deviceLabel: String =
        "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}".take(80)

    suspend fun register(nickname: String, email: String, password: String): ApiResult<UserDto> =
        apiCall(json) { api.register(RegisterRequest(nickname, email, password, deviceLabel)) }
            .alsoStoreTokens()

    suspend fun login(identifier: String, password: String): ApiResult<UserDto> =
        apiCall(json) { api.login(LoginRequest(identifier, password, deviceLabel)) }
            .alsoStoreTokens()

    /** nickname so no primeiro login desta conta Google (senao 409 needs_nickname). */
    suspend fun loginWithGoogle(idToken: String, nickname: String? = null): ApiResult<UserDto> =
        apiCall(json) { api.googleLogin(GoogleLoginRequest(idToken, nickname, deviceLabel)) }
            .alsoStoreTokens()

    suspend fun me(): ApiResult<UserDto> = apiCall(json) { api.me() }

    /** Ping de saude: so serve para atualizar Connectivity (OK / SERVER_DOWN)
     *  como efeito colateral do apiCall. O resultado em si e ignorado. */
    suspend fun ping() {
        runCatching { apiCall(json) { api.health() } }
    }

    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        if (refresh != null) {
            runCatching { NetworkModule.bareApi().logout(LogoutRequest(refresh)) }
        }
        // Limpa localmente aconteca o que acontecer: se o servidor estiver fora,
        // o usuario ainda tem que conseguir sair do app.
        runCatching { tokenStore.clear() }
    }

    private suspend fun ApiResult<TokenResponse>.alsoStoreTokens(): ApiResult<UserDto> =
        when (this) {
            is ApiResult.Ok -> {
                val u = value.user
                tokenStore.addAccount(
                    u.id, u.nickname, u.email, u.google, u.hasPassword,
                    value.accessToken, value.refreshToken,
                )
                ApiResult.Ok(u)
            }
            is ApiResult.Failure -> this
        }

    // --- troca de conta -------------------------------------------------

    val accounts: kotlinx.coroutines.flow.Flow<List<com.notifyshare.data.local.AccountInfo>> = tokenStore.accounts

    /** Conta ativa guardada no aparelho — nickname/email sem chamar o backend. */
    val activeAccount: kotlinx.coroutines.flow.Flow<com.notifyshare.data.local.AccountInfo?> =
        tokenStore.activeAccount

    suspend fun switchAccount(id: String) = tokenStore.switchTo(id)

    suspend fun removeAccount(id: String) = tokenStore.removeAccount(id)
}
