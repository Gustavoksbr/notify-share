package com.notifyshare.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.notifyshare.BuildConfig

sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
    /** Nao e erro de verdade: so quer dizer "sem conta cadastrada no aparelho
     *  pro Credential Manager escolher". O chamador deve cair pro login via
     *  navegador (ver GoogleWebAuth), do jeito que um site faz numa aba
     *  anonima — nao faz sentido travar o usuario com uma mensagem de erro. */
    data object NoAccountOnDevice : GoogleSignInResult
}

/**
 * Login com Google pelo Credential Manager — o caminho atual (o GoogleSignInClient
 * antigo foi descontinuado). `filterByAuthorizedAccounts=false` faz o seletor
 * mostrar TODAS as contas do aparelho ja no primeiro uso, com "usar outra conta"
 * embutido.
 */
class GoogleSignIn(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    val available: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    suspend fun requestIdToken(): GoogleSignInResult {
        if (!available) return GoogleSignInResult.Error("Login com Google nao esta configurado neste build")

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(cred.data).idToken)
            } else {
                GoogleSignInResult.Error("O Google devolveu uma credencial inesperada")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.NoAccountOnDevice
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error(e.message ?: "Falha no login com Google")
        }
    }
}
