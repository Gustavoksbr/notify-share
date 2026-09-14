package com.notifyshare.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.notifyshare.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Login com Google pelo navegador (Custom Tab, OAuth 2.0 + PKCE) — o mesmo
 * fluxo que um site usa numa aba anônima, com a tela de login de verdade do
 * Google (accounts.google.com), onde o usuário digita e-mail/senha na mão.
 *
 * É o fallback de [GoogleSignIn] quando o Credential Manager não acha
 * NENHUMA conta cadastrada no aparelho (`NoAccountOnDevice`) — o app nunca vê
 * a senha, só recebe de volta um código de autorização que troca por um ID
 * token, o mesmo tipo de token que `POST /auth/google` já aceita.
 *
 * Usa um client DIFERENTE do login por Credential Manager: o Google só aceita
 * redirect com esquema customizado (com.notifyshare:/...) em client tipo
 * "Android" — um client "Web application" recusa isso direto
 * ("Custom scheme URIs are not allowed for 'WEB' client type"). Por isso
 * `GOOGLE_ANDROID_CLIENT_ID` (não `GOOGLE_CLIENT_ID`) aqui, e o backend
 * (`GoogleProperties.androidClientId`) precisa aceitar esse client como
 * audience válido também — o ID token que volta traz ELE, não o Web.
 *
 * No Google Cloud Console, esse client Android precisa ter, em
 * "Configurações avançadas", o checkbox "Ativar esquema de URI personalizado"
 * marcado e salvo (pode levar de minutos a horas para propagar). O Google NÃO
 * deixa escolher esse esquema — ele é fixo, derivado do próprio client id
 * (`com.googleusercontent.apps.<id>`); é por isso que aquela tela não tem
 * campo de texto pra digitar nada, só o checkbox liga/desliga.
 */
class GoogleWebAuth(context: Context) {

    private val appContext = context.applicationContext
    private val service = AuthorizationService(appContext)

    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )

    private val redirectUri: Uri = Uri.parse("${BuildConfig.GOOGLE_ANDROID_REDIRECT_SCHEME}:/oauth2redirect")

    val available: Boolean get() = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.isNotBlank()

    /** Intent que abre a Custom Tab com a tela de login do Google. */
    fun buildAuthIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            config, BuildConfig.GOOGLE_ANDROID_CLIENT_ID, ResponseTypeValues.CODE, redirectUri,
        ).setScopes("openid", "email", "profile").build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Chamado com o Intent que voltou do redirect — troca o código pelo ID token. */
    suspend fun handleResult(intent: Intent): GoogleSignInResult {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        if (response == null) {
            return if (error?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                GoogleSignInResult.Cancelled
            } else {
                GoogleSignInResult.Error(error?.errorDescription ?: "Não deu para entrar com o Google agora")
            }
        }
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                val idToken = tokenResponse?.idToken
                cont.resume(
                    if (idToken != null) {
                        GoogleSignInResult.Token(idToken)
                    } else {
                        GoogleSignInResult.Error(exception?.errorDescription ?: "Não deu para confirmar o login")
                    },
                )
            }
        }
    }

    fun dispose() = service.dispose()
}
