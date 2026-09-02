package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.recentAppsStore by preferencesDataStore(name = "notifyshare_recent_apps")

@Serializable
private data class Hit(val pkg: String, val at: Long)

data class RecentApp(val packageName: String, val count: Int)

/**
 * Guarda no aparelho quais apps notificaram nos ultimos 7 dias, so para a tela
 * de escolher apps ("Notificaram voce recentemente"). Nada sai do celular — e
 * um ring buffer local de no maximo [CAP] eventos.
 */
class RecentAppsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun record(packageName: String) {
        val now = System.currentTimeMillis()
        val kept = read().filter { now - it.at < WINDOW_MS } + Hit(packageName, now)
        write(kept.takeLast(CAP))
    }

    /** Apps que notificaram nos ultimos 7 dias, do mais frequente ao menos. */
    suspend fun recent(): List<RecentApp> {
        val now = System.currentTimeMillis()
        return read()
            .filter { now - it.at < WINDOW_MS }
            .groupingBy { it.pkg }.eachCount()
            .map { RecentApp(it.key, it.value) }
            .sortedByDescending { it.count }
    }

    private suspend fun read(): List<Hit> {
        val raw = context.recentAppsStore.data.first()[KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Hit>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun write(hits: List<Hit>) {
        context.recentAppsStore.edit { it[KEY] = json.encodeToString(hits) }
    }

    private companion object {
        val KEY = stringPreferencesKey("hits")
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        const val CAP = 500
    }
}
