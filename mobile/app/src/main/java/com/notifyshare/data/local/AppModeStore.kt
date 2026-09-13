package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appModeStore by preferencesDataStore(name = "notifyshare_app_mode")

/**
 * Social (feed, amigos, compartilhar — precisa de conta) ou Local (cofre +
 * permissões do aparelho — sem conta, sem internet). São dois modos de peso
 * igual, não um "modo social com um extra escondido": por isso o switcher que
 * troca entre eles fica acima de tudo, nunca dentro de uma aba.
 */
enum class AppMode { SOCIAL, LOCAL }

/** Qual modo está selecionado — device-wide, independente de login/conta. */
class AppModeStore(private val context: Context) {

    val flow: Flow<AppMode> = context.appModeStore.data.map {
        if (it[KEY] == "local") AppMode.LOCAL else AppMode.SOCIAL
    }

    suspend fun set(mode: AppMode) {
        context.appModeStore.edit { it[KEY] = if (mode == AppMode.LOCAL) "local" else "social" }
    }

    private companion object {
        val KEY = stringPreferencesKey("mode")
    }
}
