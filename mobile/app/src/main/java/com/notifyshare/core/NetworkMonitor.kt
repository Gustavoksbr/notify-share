package com.notifyshare.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Sabe se o aparelho tem alguma rede com internet — sem falar com o nosso
 * servidor. Serve para distinguir "voce esta sem internet" de "o servidor esta
 * fora do ar" quando uma chamada falha.
 *
 * Alem de guardar o estado, avisa o [Connectivity] na hora em que a internet
 * cai ou volta, para a barra do app aparecer sem esperar uma chamada falhar.
 */
object NetworkMonitor {

    @Volatile
    private var hasInternet: Boolean = true

    fun init(context: Context) {
        val cm = context.applicationContext.getSystemService<ConnectivityManager>() ?: return
        hasInternet = cm.currentInternet()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update(cm.currentInternet())
            override fun onLost(network: Network) = update(cm.currentInternet())
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                update(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        })
    }

    private fun update(nowHasInternet: Boolean) {
        val changed = nowHasInternet != hasInternet
        hasInternet = nowHasInternet
        if (!changed) return
        if (nowHasInternet) Connectivity.onInternetBack() else Connectivity.onInternetLost()
    }

    /** true = tem internet mas a chamada falhou -> problema e o servidor. */
    fun deviceHasInternet(): Boolean = hasInternet

    private fun ConnectivityManager.currentInternet(): Boolean {
        val caps = getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
