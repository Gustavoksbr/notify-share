package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.languageStore by preferencesDataStore(name = "notifyshare_language")

/** "pt" | "en" — idioma da interface. Escolha do usuário, não do aparelho. */
class LanguageStore(private val context: Context) {

    val flow: Flow<String> = context.languageStore.data.map { it[KEY] ?: "pt" }

    suspend fun set(lang: String) {
        context.languageStore.edit { it[KEY] = lang }
    }

    private companion object {
        val KEY = stringPreferencesKey("lang")
    }
}
