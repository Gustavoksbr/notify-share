package com.notifyshare

import android.app.Application
import android.content.Context
import com.notifyshare.data.AuthRepository
import com.notifyshare.data.local.SecureTokenStore

/**
 * Container de dependencias feito a mao.
 *
 * Escolha deliberada: com tres objetos no grafo, o Hilt custaria KSP, plugin e
 * anotacoes para resolver um problema que ainda nao existe. Quando o grafo
 * crescer — repositorios de amigos, grants, eventos, o listener de notificacao —
 * a migracao vale a pena e mexe so aqui e nos factories dos ViewModels.
 */
class AppContainer(context: Context) {
    private val tokenStore = SecureTokenStore(context.applicationContext)
    val authRepository = AuthRepository(tokenStore)
}

class NotifyShareApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
