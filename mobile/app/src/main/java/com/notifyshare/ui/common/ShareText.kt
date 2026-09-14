package com.notifyshare.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Manda um arquivo (dados exportados, transcrição de conversa, cofre) para a folha
 * de compartilhamento do sistema — o usuário escolhe onde salvar/enviar. Grava em
 * disco e compartilha via FileProvider para que o app recebedor veja um arquivo de
 * verdade (com o nome e mimetype certos), não um texto solto colável em qualquer campo.
 */
fun shareTextExport(context: Context, suggestedName: String, text: String, mime: String = "application/json") {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, suggestedName)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, suggestedName)
                    putExtra(Intent.EXTRA_SUBJECT, suggestedName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Exportar",
            ),
        )
    }
}
