package com.notifyshare.ui.vault

import com.notifyshare.data.local.VaultItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Filtro do cofre. Como tudo aqui é local (nunca sai do aparelho, nem passa
 * por paginação de servidor), dá para filtrar por data exata em vez de só os
 * atalhos "hoje"/"7 dias" que o feed social tem.
 */
data class VaultQuery(
    val packageName: String? = null,
    /** message | system */
    val type: String? = null,
    val sender: String? = null,
    /** all | today | 7d | custom */
    val period: String = "all",
    /** só quando period == "custom" — dia inicial/final (inclusive). */
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(packageName, type, sender, period.takeIf { it != "all" }).size
}

fun VaultItem.matches(q: VaultQuery): Boolean {
    if (q.packageName != null && packageName != q.packageName) return false
    if (q.type != null && eventType != q.type) return false
    if (q.sender != null && sender != q.sender) return false
    if (q.period == "all") return true

    val day = runCatching { Instant.parse(occurredAt) }.getOrNull()
        ?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: return true

    return when (q.period) {
        "today" -> day == LocalDate.now()
        "7d" -> !day.isBefore(LocalDate.now().minusDays(6))
        "custom" -> (q.fromDate == null || !day.isBefore(q.fromDate)) &&
            (q.toDate == null || !day.isAfter(q.toDate))
        else -> true
    }
}
