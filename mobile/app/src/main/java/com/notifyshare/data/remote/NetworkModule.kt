package com.notifyshare.data.remote

import com.notifyshare.BuildConfig
import com.notifyshare.data.local.SecureTokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Cliente sem interceptor nem authenticator, usado só para o refresh.
     * Se o refresh passasse pelo authenticator, um 401 nele dispararia outro
     * refresh, e assim por diante.
     */
    private fun bareRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()

    fun bareApi(): NotifyShareApi = bareRetrofit().create(NotifyShareApi::class.java)

    fun api(tokenStore: SecureTokenStore): NotifyShareApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(jsonMediaType))
            .build()
            .create(NotifyShareApi::class.java)
    }
}

/** Anexa o access token, menos nas rotas que existem justamente para obtê-lo. */
private class AuthInterceptor(private val tokenStore: SecureTokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.isPublicAuthRoute()) return chain.proceed(request)

        val token = runBlocking { tokenStore.accessToken() }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }
}

/**
 * Renova a sessão quando o backend devolve 401.
 *
 * O access token dura 15 minutos, então isso acontece de forma rotineira e o
 * usuário nunca deve perceber. Se o refresh também falhar, a sessão acabou de
 * verdade: limpamos a conta ativa e a UI reage sozinha, porque observa
 * `hasSession`.
 *
 * O refresh passa por um [Mutex] (single-flight): quando várias chamadas tomam
 * 401 quase juntas — o que acontece toda vez que o app abre e dispara feed +
 * presença + grants —, só a primeira renova. As outras, ao pegar o lock, veem
 * que o access token já mudou e apenas repetem a request com o token novo, sem
 * reenviar o refresh já rotacionado (o que o backend leria como vazamento).
 */
private class TokenAuthenticator(private val tokenStore: SecureTokenStore) : Authenticator {

    private val refreshLock = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Uma tentativa só. Sem isto, um refresh que devolve 401 vira laço infinito.
        if (response.priorResponse != null) return null
        if (response.request.url.encodedPath.isPublicAuthRoute()) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val usableToken = runBlocking {
            refreshLock.withLock {
                // Outra chamada pode ter renovado enquanto esperávamos o lock.
                val current = tokenStore.accessToken()
                if (!current.isNullOrBlank() && current != failedToken) return@withLock current

                val refreshToken = tokenStore.refreshToken() ?: return@withLock null
                val body = runCatching {
                    NetworkModule.bareApi().refresh(RefreshRequest(refreshToken))
                }.getOrNull()?.takeIf { it.isSuccessful }?.body()

                if (body == null) {
                    // Inclui o caso refresh_token_reused: a família foi revogada
                    // e não há como recuperar sem passar pelo login de novo.
                    runCatching { tokenStore.clear() }
                    null
                } else {
                    tokenStore.save(body.accessToken, body.refreshToken)
                    body.accessToken
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $usableToken")
            .build()
    }
}

private fun String.isPublicAuthRoute(): Boolean =
    endsWith("/auth/login") || endsWith("/auth/register") ||
        endsWith("/auth/refresh") || endsWith("/auth/logout")
