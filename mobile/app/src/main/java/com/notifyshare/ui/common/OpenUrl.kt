package com.notifyshare.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** URL da política de privacidade — hospedada no GitHub Pages do projeto. */
const val PRIVACY_POLICY_URL = "https://gustavoksbr.github.io/notify-share/privacy.html"

/** Abre uma URL no navegador do sistema. */
fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
