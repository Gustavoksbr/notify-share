package com.notifyshare.ui

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.notifyshare.MainActivity

/**
 * Reinicia a UI depois de trocar de conta, adicionar conta ou sair.
 *
 * `Activity.recreate()` sozinho NAO resolve: o `ComponentActivity` retem o
 * `ViewModelStore` atraves do recreate (trata como mudanca de configuracao),
 * entao os ViewModels da conta anterior sobreviviam com o estado antigo —
 * texto digitado na busca de amigos, "pedido enviado", listas — e so sumia
 * quando o usuario recarregava na mao.
 *
 * Limpar o store aqui derruba tanto os ViewModels de Activity quanto os de
 * NavBackStackEntry (guardados pelo NavControllerViewModel, que tambem e
 * scoped na Activity), e cancela os `viewModelScope` em andamento da conta
 * antiga.
 */
fun restartUiForAccountChange(context: Context, openTarget: String? = null) {
    val activity = context as? ComponentActivity ?: return
    activity.viewModelStore.clear()
    // A Activity recriada le o mesmo Intent. Reescrevemos o alvo (ou apagamos um
    // deep link antigo) para cair sempre na tela certa depois da troca de conta.
    activity.intent = (activity.intent ?: Intent()).apply {
        if (openTarget != null) putExtra(MainActivity.EXTRA_OPEN, openTarget)
        else removeExtra(MainActivity.EXTRA_OPEN)
    }
    activity.recreate()
}
