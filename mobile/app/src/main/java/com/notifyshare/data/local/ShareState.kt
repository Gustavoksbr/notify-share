package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.shareDataStore by preferencesDataStore(name = "notifyshare_share")

/**
 * Estado leve do compartilhamento, lido pelo NotificationListener e pelo servico
 * em primeiro plano sem precisar de rede: "ha algum grant meu ativo?" e "com
 * quantas pessoas?".
 */
class ShareState(private val context: Context) {

    val hasActiveShare: Flow<Boolean> =
        context.shareDataStore.data.map { it[KEY_ACTIVE] ?: false }

    suspend fun isActiveNow(): Boolean = context.shareDataStore.data.first()[KEY_ACTIVE] ?: false

    suspend fun peopleCount(): Int = context.shareDataStore.data.first()[KEY_COUNT] ?: 0

    suspend fun update(active: Boolean, people: Int) {
        context.shareDataStore.edit {
            it[KEY_ACTIVE] = active
            it[KEY_COUNT] = people
        }
    }

    private companion object {
        val KEY_ACTIVE = booleanPreferencesKey("has_active_share")
        val KEY_COUNT = intPreferencesKey("share_people_count")
    }
}
