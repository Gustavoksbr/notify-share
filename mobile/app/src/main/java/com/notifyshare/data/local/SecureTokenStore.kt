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

    // --- tokens da conta ativa (usado pelo NetworkModule) ----------------

    suspend fun accessToken(): String? = activeStored()?.access

    suspend fun refreshToken(): String? = activeStored()?.refresh

    suspend fun saveAccessToken(accessToken: String) = updateActive { it.copy(access = accessToken) }

    /** Chamado pelo authenticator apos um refresh bem-sucedido. */
    suspend fun save(accessToken: String, refreshToken: String) =
        updateActive { it.copy(access = accessToken, refresh = refreshToken) }

    /** Chamado no logout e quando o refresh falha: remove a conta ATIVA. */
    suspend fun clear() {
        val current = readAccounts()
        val activeId = readActiveId() ?: return
        writeAccounts(current.filterNot { it.id == activeId })
        setActiveId(readAccounts().firstOrNull()?.id)
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
        val updated = readAccounts().filterNot { it.id == id } + account
        writeAccounts(updated)
        setActiveId(id)
    }

    suspend fun switchTo(id: String) {
        if (readAccounts().any { it.id == id }) setActiveId(id)
    }

    suspend fun removeAccount(id: String) {
        writeAccounts(readAccounts().filterNot { it.id == id })
        if (readActiveId() == id) setActiveId(readAccounts().firstOrNull()?.id)
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

    private suspend fun updateActive(transform: (StoredAccount) -> StoredAccount) {
        val activeId = readActiveId() ?: return
        writeAccounts(readAccounts().map { if (it.id == activeId) transform(it) else it })
    }

    private suspend fun writeAccounts(list: List<StoredAccount>) {
        val encoded = encrypt(json.encodeToString(list))
        context.dataStore.edit { it[KEY_ACCOUNTS] = encoded }
    }

    private suspend fun setActiveId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = id
        }
    }

    private fun decodeAccounts(blob: String?): List<StoredAccount> {
        val plain = blob?.let(::decrypt) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredAccount>>(plain) }.getOrDefault(emptyList())
    }

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
