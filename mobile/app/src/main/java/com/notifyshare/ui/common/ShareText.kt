package com.notifyshare.ui.common

import android.content.Context
import android.content.Intent

/**
 * Manda um texto (dados exportados, transcrição de conversa, cofre) para a folha
 * de compartilhamento do sistema — o usuário escolhe onde salvar/enviar.
 */
fun shareTextExport(context: Context, suggestedName: String, text: String, mime: String = "text/plain") {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, suggestedName)
                    putExtra(Intent.EXTRA_SUBJECT, suggestedName)
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Exportar",
            ),
        )
    }
}
