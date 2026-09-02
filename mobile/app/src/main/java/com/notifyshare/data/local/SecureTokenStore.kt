package com.notifyshare.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "notifyshare_session")

/** Uma conta guardada no aparelho, com os tokens. */
@Serializable
data class StoredAccount(
    val id: String,
    val nickname: String,
    val email: String,
    val google: Boolean = false,
    val hasPassword: Boolean = true,
    val access: String,
    val refresh: String,
)

/** Versao publica, sem tokens — para a UI de trocar de conta. */
data class AccountInfo(
    val id: String,
    val nickname: String,
    val email: String,
    val google: Boolean,
    val active: Boolean,
)

/**
 * Multi-conta. A lista inteira e cifrada com uma chave do Android Keystore e
 * guardada como um blob no DataStore; `activeAccountId` fica em claro (e so um
 * UUID). Trocar de conta e so mudar o ponteiro — o refresh token guardado ainda
 * vale, entao nao precisa reautenticar.
 *
 * Os metodos accessToken/refreshToken/save/clear operam sempre na conta ATIVA,
 * entao o NetworkModule (interceptor + authenticator) nao muda.
 */
class SecureTokenStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Serializa TODAS as mutacoes da lista de contas. Sem isto, o authenticator
     * (que roda em thread do OkHttp via runBlocking) podia gravar por cima de um
     * switchTo/addAccount concorrente, e a lista de contas terminava truncada.
     */
    private val mutex = Mutex()

    // --- tokens da conta ativa (usado pelo NetworkModule) ----------------

    suspend fun accessToken(): String? = activeStored()?.access

    suspend fun refreshToken(): String? = activeStored()?.refresh

    /** Id da conta ativa agora, leitura unica. Usado para separar o cache por conta. */
    suspend fun activeId(): String? = readActiveId()

    suspend fun saveAccessToken(accessToken: String) = mutate { accounts, active ->
        accounts.map { if (it.id == active) it.copy(access = accessToken) else it } to active
    }

    /** Chamado pelo authenticator apos um refresh bem-sucedido. */
    suspend fun save(accessToken: String, refreshToken: String) = mutate { accounts, active ->
        accounts.map {
            if (it.id == active) it.copy(access = accessToken, refresh = refreshToken) else it
        } to active
    }

    /** Chamado no logout e quando o refresh falha: remove a conta ATIVA. */
    suspend fun clear() = mutate { accounts, active ->
        val remaining = accounts.filterNot { it.id == active }
        remaining to remaining.firstOrNull()?.id
    }

    // --- multi-conta ----------------------------------------------------

    /** Adiciona (ou atualiza) a conta e a deixa ativa. Chamado no login. */
    suspend fun addAccount(
        id: String,
        nickname: String,
        email: String,
        google: Boolean,
        hasPassword: Boolean,
        access: String,
        refresh: String,
    ) {
        val account = StoredAccount(id, nickname, email, google, hasPassword, access, refresh)
        // Login em andamento: se o blob antigo estiver corrompido, tudo bem
        // recomecar dele — o usuario acabou de se autenticar.
        mutate(allowOverwriteOnCorruption = true) { accounts, _ ->
            (accounts.filterNot { it.id == id } + account) to id
        }
    }

    suspend fun switchTo(id: String) = mutate { accounts, active ->
        accounts to (if (accounts.any { it.id == id }) id else active)
    }

    suspend fun removeAccount(id: String) = mutate { accounts, active ->
        val remaining = accounts.filterNot { it.id == id }
        remaining to (if (active == id) remaining.firstOrNull()?.id else active)
    }

    suspend fun activeTokensFor(id: String): Pair<String, String>? =
        readAccounts().firstOrNull { it.id == id }?.let { it.access to it.refresh }

    val accounts: Flow<List<AccountInfo>> = context.dataStore.data.map { prefs ->
        val activeId = prefs[KEY_ACTIVE]
        decodeAccounts(prefs[KEY_ACCOUNTS]).map {
            AccountInfo(it.id, it.nickname, it.email, it.google, active = it.id == activeId)
        }
    }

    /** Conta ativa (sem tokens) — fonte local-first para nickname/email/perfil. */
    val activeAccount: Flow<AccountInfo?> = context.dataStore.data.map { prefs ->
        val activeId = prefs[KEY_ACTIVE] ?: return@map null
        decodeAccounts(prefs[KEY_ACCOUNTS]).firstOrNull { it.id == activeId }?.let {
            AccountInfo(it.id, it.nickname, it.email, it.google, active = true)
        }
    }

    /** true enquanto houver uma conta ativa. */
    val hasSession: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val activeId = prefs[KEY_ACTIVE] ?: return@map false
        decodeAccounts(prefs[KEY_ACCOUNTS]).any { it.id == activeId && it.refresh.isNotBlank() }
    }

    // --- token do FCM (nivel de aparelho, nao de conta) -----------------

    suspend fun savePendingFcmToken(token: String) {
        context.dataStore.edit { it[KEY_FCM_PENDING] = token }
    }

    suspend fun pendingFcmToken(): String? = context.dataStore.data.first()[KEY_FCM_PENDING]

    suspend fun clearPendingFcmToken(confirmed: String) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_FCM_PENDING] == confirmed) prefs.remove(KEY_FCM_PENDING)
        }
    }

    val fcmStatus: Flow<String?> = context.dataStore.data.map { it[KEY_FCM_STATUS] }

    suspend fun setFcmStatus(status: String) {
        context.dataStore.edit { it[KEY_FCM_STATUS] = status }
    }

    // --- internos -----------------------------------------------------

    private suspend fun readAccounts(): List<StoredAccount> =
        decodeAccounts(context.dataStore.data.first()[KEY_ACCOUNTS])

    private suspend fun readActiveId(): String? = context.dataStore.data.first()[KEY_ACTIVE]

    private suspend fun activeStored(): StoredAccount? {
        val activeId = readActiveId() ?: return null
        return readAccounts().firstOrNull { it.id == activeId }
    }

    /**
     * Le a lista + o id ativo, aplica [block] e grava os dois de uma vez, tudo
     * sob o [mutex] e dentro de um unico `edit {}` (atomico no DataStore).
     *
     * Se o blob existe mas nao decifra, a escrita e abortada por padrao: sobre-
     * escrever ali apagaria contas que talvez voltem a decifrar no proximo boot.
     */
    private suspend fun mutate(
        allowOverwriteOnCorruption: Boolean = false,
        block: (accounts: List<StoredAccount>, activeId: String?) -> Pair<List<StoredAccount>, String?>,
    ) = mutex.withLock {
        context.dataStore.edit { prefs ->
            val blob = prefs[KEY_ACCOUNTS]
            val decoded = decodeAccountsOrNull(blob)
            check(decoded != null || blob == null || allowOverwriteOnCorruption) {
                "conta cifrada ilegivel; escrita abortada para nao apagar dados"
            }
            val (newList, newActive) = block(decoded ?: emptyList(), prefs[KEY_ACTIVE])
            prefs[KEY_ACCOUNTS] = encrypt(json.encodeToString(newList))
            if (newActive == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = newActive
        }
    }

    /** null = o blob existe mas nao decifra/parseia. emptyList = nao ha blob. */
    private fun decodeAccountsOrNull(blob: String?): List<StoredAccount>? {
        if (blob == null) return emptyList()
        val plain = decrypt(blob) ?: return null
        return runCatching { json.decodeFromString<List<StoredAccount>>(plain) }.getOrNull()
    }

    private fun decodeAccounts(blob: String?): List<StoredAccount> = decodeAccountsOrNull(blob) ?: emptyList()

    // --- cifragem ---------------------------------------------------------

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.b64()}:${encrypted.b64()}"
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (ivPart, dataPart) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivPart, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "notifyshare_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128

        val KEY_ACCOUNTS = stringPreferencesKey("accounts_v2")
        val KEY_ACTIVE = stringPreferencesKey("active_account")
        val KEY_FCM_PENDING = stringPreferencesKey("fcm_token_pending")
        val KEY_FCM_STATUS = stringPreferencesKey("fcm_status")
    }
}
