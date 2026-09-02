package com.notifyshare.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Tela de "iniciar em segundo plano" / autostart. Cada fabricante enterra num
 * lugar diferente, e a maioria dos aparelhos (Android puro, Samsung, Motorola)
 * nem tem — nesse caso devolve null e o app simplesmente nao mostra o card.
 *
 * Isto e sempre OPCIONAL: sem autostart o app funciona normalmente enquanto
 * estiver aberto; autostart so ajuda a acordar em segundo plano e a se reabrir
 * depois que o sistema mata o processo.
 */
object OemSettings {

    private val AUTOSTART_CANDIDATES = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
    )

    /** Intent para a tela de autostart, ou null se o aparelho nao tiver. */
    fun autostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for ((pkg, cls) in AUTOSTART_CANDIDATES) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return null
    }

    fun hasAutostartScreen(context: Context): Boolean = autostartIntent(context) != null
}
