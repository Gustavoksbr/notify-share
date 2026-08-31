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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "notifyshare_session")

/**
 * Guarda os tokens cifrados com uma chave do Android Keystore.
 *
 * Por que nao SharedPreferences puro: os tokens dao acesso a conta, e o
 * armazenamento em claro fica legivel em aparelho com root ou por backup.
 *
 * Por que nao EncryptedSharedPreferences: a biblioteca androidx.security-crypto
 * foi descontinuada. O caminho atual e fazer o AES/GCM na mao com uma chave do
 * Keystore — que nunca sai do hardware — e guardar so o texto cifrado no
 * DataStore.
 */
class SecureTokenStore(private val context: Context) {

    suspend fun save(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS] = encrypt(accessToken)
            prefs[KEY_REFRESH] = encrypt(refreshToken)
        }
    }

    suspend fun saveAccessToken(accessToken: String) {
        context.dataStore.edit { it[KEY_ACCESS] = encrypt(accessToken) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun accessToken(): String? = read(KEY_ACCESS)

    suspend fun refreshToken(): String? = read(KEY_REFRESH)

    /** Emite true enquanto houver refresh token guardado. */
    val hasSession: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_REFRESH] != null }

    private suspend fun read(key: Preferences.Key<String>): String? =
        context.dataStore.data.first()[key]?.let(::decrypt)

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

    /** Guarda IV e texto cifrado juntos: "iv:ciphertext", ambos em base64. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "${encrypted.iv(cipher)}:${encrypted.b64()}"
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
    }.getOrNull() // chave trocada ou dado corrompido: trata como "sem sessao"

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun ByteArray.iv(cipher: Cipher): String = cipher.iv.b64()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "notifyshare_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128

        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
    }
}
