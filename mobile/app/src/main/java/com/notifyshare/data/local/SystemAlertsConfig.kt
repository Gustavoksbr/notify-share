package com.notifyshare.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.systemAlertsStore by preferencesDataStore(name = "notifyshare_system_alerts")

/** Pseudo-pacote do "Meu celular": alertas que o proprio aparelho gera. */
const val PKG_SYSTEM_PHONE = "system:phone"

/**
 * Config dos alertas de "Meu celular". Global (nao por conta, nao por grant):
 * "quando meu celular avisa bateria baixa" e naturalmente um ajuste do aparelho.
 * A granularidade por amigo continua existindo pelo liga/desliga do app
 * "Meu celular" em cada compartilhamento.
 *
 * Os eventos so sao emitidos com o servico em segundo plano ativo (= algum
 * compartilhamento ligado) — o `ShareForegroundService` os escuta.
 */
data class SystemAlerts(
    val batteryLow: Boolean = true,
    val batteryLowPct: Int = 15,
    val batteryHigh: Boolean = true,
    val batteryHighPct: Int = 80,
    val charging: Boolean = true,
    val reboot: Boolean = true,
    val dailyDrain: Boolean = false,
) {
    val anyEnabled: Boolean get() = batteryLow || batteryHigh || charging || reboot || dailyDrain
}

/** Estado corrente do resumo diario de bateria (persistido entre reinicios do serviço). */
data class DrainState(val epochDay: Long, val lastLevel: Int, val drainAccum: Int)

class SystemAlertsConfig(private val context: Context) {

    val flow: Flow<SystemAlerts> = context.systemAlertsStore.data.map { p ->
        SystemAlerts(
            batteryLow = p[BL] ?: true,
            batteryLowPct = p[BLP] ?: 15,
            batteryHigh = p[BH] ?: true,
            batteryHighPct = p[BHP] ?: 80,
            charging = p[CHG] ?: true,
            reboot = p[RBT] ?: true,
            dailyDrain = p[DD] ?: false,
        )
    }

    suspend fun current(): SystemAlerts = flow.first()

    suspend fun update(block: (SystemAlerts) -> SystemAlerts) {
        val next = block(current())
        context.systemAlertsStore.edit { p ->
            p[BL] = next.batteryLow
            p[BLP] = next.batteryLowPct.coerceIn(5, 60)
            p[BH] = next.batteryHigh
            p[BHP] = next.batteryHighPct.coerceIn(40, 100)
            p[CHG] = next.charging
            p[RBT] = next.reboot
            p[DD] = next.dailyDrain
        }
    }

    suspend fun drainState(): DrainState? {
        val p = context.systemAlertsStore.data.first()
        val day = p[DD_DAY] ?: return null
        return DrainState(day, p[DD_LAST] ?: 100, p[DD_ACC] ?: 0)
    }

    suspend fun setDrainState(state: DrainState) {
        context.systemAlertsStore.edit { p ->
            p[DD_DAY] = state.epochDay
            p[DD_LAST] = state.lastLevel
            p[DD_ACC] = state.drainAccum
        }
    }

    private companion object {
        val BL = booleanPreferencesKey("battery_low")
        val BLP = intPreferencesKey("battery_low_pct")
        val BH = booleanPreferencesKey("battery_high")
        val BHP = intPreferencesKey("battery_high_pct")
        val CHG = booleanPreferencesKey("charging")
        val RBT = booleanPreferencesKey("reboot")
        val DD = booleanPreferencesKey("daily_drain")
        val DD_DAY = longPreferencesKey("drain_day")
        val DD_LAST = intPreferencesKey("drain_last")
        val DD_ACC = intPreferencesKey("drain_acc")
    }
}
