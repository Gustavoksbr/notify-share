package com.notifyshare.data

import com.notifyshare.core.Connectivity
import com.notifyshare.core.NetState
import com.notifyshare.core.NetworkMonitor
import com.notifyshare.data.remote.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Resultado de uma chamada.
 *
 * O erro carrega o `code` do backend para a tela poder reagir a ele, e o
 * `field` para destacar o campo certo do formulario.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(
        val code: String,
        val message: String,
        val field: String? = null,
        val attemptsRemaining: Int? = null,
        val retryAfterSeconds: Long? = null,
    ) : ApiResult<Nothing>

    companion object {
        /** Falha de I/O: nao houve resposta. O motivo (sem internet x servidor
         *  fora) fica em Connectivity.state; aqui so marca que a chamada nao foi. */
        val offline = Failure(code = "offline", message = "Nao foi possivel completar agora")
    }
}

val <T> ApiResult<T>.valueOrNull: T? get() = (this as? ApiResult.Ok)?.value

inline fun <T> ApiResult<T>.onFailure(block: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(this)
    return this
}

/** Traduz resposta, corpo de erro padronizado e falha de rede. */
suspend fun <T> apiCall(json: Json, block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    Connectivity.markOk()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> ApiResult.Ok(body)
        response.isSuccessful -> @Suppress("UNCHECKED_CAST") ApiResult.Ok(Unit as T)
        else -> response.errorBody()?.string().toApiFailure(json)
    }
} catch (e: IOException) {
    Connectivity.markFailure()
    ApiResult.offline
} catch (e: Exception) {
    ApiResult.Failure("unknown_error", e.message ?: "Falha inesperada")
}

/**
 * A falha foi por conexao (sem internet, servidor fora, ou token expirado sem
 * rede para renovar — que chega como 401)? Nesse caso as telas caem no cache
 * local em vez de mostrar erro. Erro real do servidor (500, validacao) nao.
 */
fun isConnectivityFailure(failure: ApiResult.Failure): Boolean =
    failure.code == "offline" ||
        !NetworkMonitor.deviceHasInternet() ||
        Connectivity.state.value != NetState.OK ||
        failure.code == "unauthorized" ||
        failure.code == "invalid_token"

fun String?.toApiFailure(json: Json): ApiResult.Failure {
    if (isNullOrBlank()) return ApiResult.Failure("unknown_error", "Nao foi possivel completar a operacao")
    return runCatching { json.decodeFromString<ApiErrorDto>(this) }
        .map { ApiResult.Failure(it.code, it.message, it.field, it.attemptsRemaining, it.retryAfterSeconds) }
        .getOrElse { ApiResult.Failure("unknown_error", "Nao foi possivel completar a operacao") }
}
