package com.notifyshare.ui.format

import com.notifyshare.data.remote.FeedItemDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private val json = Json { ignoreUnknownKeys = true }
private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun parseInstantOrNull(iso: String?): Instant? =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun shortTime(iso: String): String =
    parseInstantOrNull(iso)?.atZone(zone)?.format(timeFmt) ?: ""

fun relativeShort(iso: String?): String {
    val instant = parseInstantOrNull(iso) ?: return ""
    val mins = ChronoUnit.MINUTES.between(instant, Instant.now())
    return when {
        mins < 1 -> "agora"
        mins < 60 -> "ha ${mins}min"
        mins < 60 * 24 -> "ha ${mins / 60}h"
        else -> "ha ${mins / (60 * 24)}d"
    }
}

fun dayBucket(iso: String): String {
    val date = parseInstantOrNull(iso)?.atZone(zone)?.toLocalDate() ?: return ""
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> date.format(dateFmt)
    }
}

private fun FeedItemDto.contentObject(): JsonObject? =
    content?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

fun friendlyPackage(pkg: String): String = when {
    pkg.startsWith("system:") -> pkg.removePrefix("system:").replaceFirstChar(Char::uppercase)
    pkg.contains('.') -> pkg.substringAfterLast('.').replaceFirstChar(Char::uppercase)
    else -> pkg
}

fun eventTitle(row: FeedItemDto): String {
    val obj = row.contentObject()
    val app = obj?.str("appLabel") ?: obj?.str("title") ?: friendlyPackage(row.packageName)
    val sender = obj?.str("sender")
    return when {
        row.eventType == "battery" -> "Bateria"
        row.eventType == "network" -> "Wi-Fi"
        sender != null -> "$app · $sender"
        else -> app
    }
}

fun eventBody(row: FeedItemDto): String = when (row.mode) {
    "sender_only" -> "Conteudo oculto — so o remetente"
    else -> row.contentObject()?.str("body") ?: "Nova notificacao"
}
