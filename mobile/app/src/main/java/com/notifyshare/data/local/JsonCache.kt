package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val Context.cacheStore by preferencesDataStore(name = "notifyshare_cache")

/**
 * Cache local simples: guarda a ultima resposta de cada tela como JSON no
 * DataStore. Os dados sao limitados (feed de 7 dias, conversas paginadas), entao
 * nao vale um banco. Se crescer, a troca por Room mexe so aqui.
 *
 * Serve o modo offline: a tela mostra o cache na hora e, se a rede falhar,
 * continua mostrando o cache com o aviso de "offline".
 */
class JsonCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        val encoded = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return
        context.cacheStore.edit { it[stringPreferencesKey(key)] = encoded }
    }

    suspend fun <T> load(key: String, serializer: KSerializer<T>): T? {
        val raw = context.cacheStore.data.first()[stringPreferencesKey(key)] ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    suspend fun clear() {
        context.cacheStore.edit { it.clear() }
    }
}
