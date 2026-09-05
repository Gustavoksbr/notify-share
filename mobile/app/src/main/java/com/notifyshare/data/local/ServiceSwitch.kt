package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serviceSwitchStore by preferencesDataStore(name = "notifyshare_service_switch")

/**
 * Interruptor mestre do compartilhamento em segundo plano. Global (nao por
 * conta), ligado por padrao. Desligado por "Desligar" na notificacao fixa ou
 * pelo card no topo de Permissoes; para o servico e nada mais — o app continua
 * funcionando quando aberto.
 */
class ServiceSwitch(private val context: Context) {

    val enabled: Flow<Boolean> =
        context.serviceSwitchStore.data.map { it[KEY] ?: true }

    suspend fun enabledNow(): Boolean = context.serviceSwitchStore.data.first()[KEY] ?: true

    suspend fun set(value: Boolean) {
        context.serviceSwitchStore.edit { it[KEY] = value }
    }

    private companion object {
        val KEY = booleanPreferencesKey("enabled")
    }
}
