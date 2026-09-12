package com.notifyshare.ui.chat

import com.notifyshare.data.remote.TimelineItemDto
import com.notifyshare.ui.format.fullDateTime
import java.time.Instant

/** Transcrição da conversa em texto puro, do mais antigo para o mais novo. */
fun buildTranscript(nickname: String, items: List<TimelineItemDto>): String {
    val chrono = items.sortedBy { it.at }
    return buildString {
        appendLine("Conversa com @$nickname — Notify Share")
        appendLine("Exportada em ${fullDateTime(Instant.now().toString())}")
        appendLine("${chrono.size} itens")
        appendLine()
        chrono.forEach { item ->
            val time = fullDateTime(item.at)
            if (item.kind == "audit") {
                appendLine("[$time] — ${auditLabel(item)}")
            } else {
                val who = if (item.mine) "Eu" else "@$nickname"
                val body = when {
                    item.deleted -> "(mensagem apagada)"
                    !item.body.isNullOrBlank() -> item.body
                    else -> "(sem texto)"
                }
                val edited = if (item.edited) " (editada)" else ""
                item.replyTo?.takeIf { !it.deleted }?.let {
                    appendLine("[$time] $who (respondendo \"${it.preview.take(60)}\"):")
                    appendLine("  $body$edited")
                } ?: appendLine("[$time] $who: $body$edited")
                item.linkedEvent?.preview?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   ↳ notificação: $it")
                }
            }
        }
    }
}

private fun auditLabel(item: TimelineItemDto): String = when (item.action) {
    "activated" -> "Compartilhamento ativado"
    "paused" -> "Compartilhamento pausado"
    "resumed" -> "Compartilhamento retomado"
    "revoked" -> "Compartilhamento encerrado"
    "offered" -> "Ofereceu compartilhar"
    "requested" -> "Pediu para receber"
    else -> item.action.orEmpty()
}
