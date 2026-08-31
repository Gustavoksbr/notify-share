package com.notifyshare.data

import android.os.Build
import com.notifyshare.data.local.SecureTokenStore
import com.notifyshare.data.remote.ApiErrorDto
import com.notifyshare.data.remote.LoginRequest
import com.notifyshare.data.remote.LogoutRequest
import com.notifyshare.data.remote.NetworkModule
import com.notifyshare.data.remote.NotifyShareApi
import com.notifyshare.data.remote.RegisterRequest
import com.notifyshare.data.remote.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Resultado de uma chamada.
 *
 * O erro carrega o `code` do backend para a tela poder reagir a ele, e o
 * `field` para destacar o campo certo do formulário.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(
        val code: String,
        val message: String,
        val field: String? = null,
    ) : ApiResult<Nothing>

    companion object {
        /** Rede fora do ar: erro local, não veio resposta nenhuma do servidor. */
        val offline = Failure(
            code = "offline",
            message = "Sem conexao com o servidor. Confira se o backend esta rodando " +
                "e se a ponte USB esta ativa (adb reverse tcp:8080 tcp:8080).",
        )
    }
}

class AuthRepository(
    private val tokenStore: SecureTokenStore,
    private val api: NotifyShareApi = NetworkModule.api(tokenStore),
    private val json: Json = NetworkModule.json,
) {

    val hasSession: Flow<Boolean> = tokenStore.hasSession

    private val deviceLabel: String =
        "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}".take(80)

    suspend fun register(nickname: String, email: String, password: String): ApiResult<UserDto> =
        call { api.register(RegisterRequest(nickname, email, password, deviceLabel)) }
            .alsoStoreTokens()

    suspend fun login(identifier: String, password: String): ApiResult<UserDto> =
        call { api.login(LoginRequest(identifier, password, deviceLabel)) }
            .alsoStoreTokens()

    suspend fun me(): ApiResult<UserDto> = call { api.me() }

    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        if (refresh != null) {
            runCatching { NetworkModule.bareApi().logout(LogoutRequest(refresh)) }
        }
        // Limpa localmente aconteça o que acontecer: se o servidor estiver fora,
        // o usuário ainda tem que conseguir sair do app.
        tokenStore.clear()
    }

    // --- infraestrutura ---------------------------------------------------

    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> ApiResult.Ok(body)
            response.isSuccessful -> ApiResult.Failure("empty_body", "Resposta vazia do servidor")
            else -> response.errorBody()?.string().toFailure()
        }
    } catch (e: IOException) {
        ApiResult.offline
    }

    private fun String?.toFailure(): ApiResult.Failure {
        if (this.isNullOrBlank()) {
            return ApiResult.Failure("unknown_error", "Nao foi possivel completar a operacao")
        }
        return runCatching { json.decodeFromString<ApiErrorDto>(this) }
            .map { ApiResult.Failure(it.code, it.message, it.field) }
            .getOrElse {
                ApiResult.Failure("unknown_error", "Nao foi possivel completar a operacao")
            }
    }

    /**
     * Register e login devolvem os tokens junto com o usuário. Guardamos os
     * tokens aqui e entregamos só o usuário para cima: nenhuma camada acima
     * desta precisa saber que token existe.
     */
    private suspend fun ApiResult<com.notifyshare.data.remote.TokenResponse>.alsoStoreTokens(): ApiResult<UserDto> =
        when (this) {
            is ApiResult.Ok -> {
                tokenStore.save(value.accessToken, value.refreshToken)
                ApiResult.Ok(value.user)
            }
            is ApiResult.Failure -> this
        }
}
