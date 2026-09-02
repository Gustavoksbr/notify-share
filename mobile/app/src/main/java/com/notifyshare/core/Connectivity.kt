package com.notifyshare.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetState { OK, NO_INTERNET, SERVER_DOWN }

/**
 * Estado da conexao, observado pela barra no topo do app.
 *
 * Duas fontes alimentam isto:
 *  - [NetworkMonitor] avisa na hora quando a internet do aparelho cai/volta;
 *  - [com.notifyshare.data.apiCall] e o poller de saude marcam OK/falha a cada
 *    chamada real ao servidor.
 *
 * Assim conseguimos separar "voce esta sem internet" de "o servidor esta fora".
 */
object Connectivity {

    private val _state = MutableStateFlow(NetState.OK)
    val state: StateFlow<NetState> = _state.asStateFlow()

    /** Chamada ao servidor deu certo. */
    fun markOk() {
        _state.value = NetState.OK
    }

    /** Chamada ao servidor falhou por I/O. */
    fun markFailure() {
        _state.value = if (NetworkMonitor.deviceHasInternet()) NetState.SERVER_DOWN else NetState.NO_INTERNET
    }

    /** O aparelho perdeu qualquer rede — mostra na hora, sem esperar uma chamada. */
    fun onInternetLost() {
        _state.value = NetState.NO_INTERNET
    }

    /** A rede voltou; otimista ate a proxima chamada confirmar. */
    fun onInternetBack() {
        if (_state.value == NetState.NO_INTERNET) _state.value = NetState.OK
    }

    /**
     * Chamado quando o usuario puxa a tela para recarregar. Se o aparelho nao
     * tem rede, ja mostra "sem internet" na hora, sem esperar a chamada falhar.
     */
    fun probeBeforeRefresh() {
        if (!NetworkMonitor.deviceHasInternet()) _state.value = NetState.NO_INTERNET
    }
}
