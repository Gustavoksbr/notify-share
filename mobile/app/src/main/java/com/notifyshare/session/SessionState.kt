package com.notifyshare.session

import com.notifyshare.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quem esta logado agora. Preenchido pela HomeViewModel ao carregar /me e lido
 * pelas telas que precisam do proprio nickname (Feed, Perfil, Conversa).
 */
class SessionState {

    private val _me = MutableStateFlow<UserDto?>(null)
    val me: StateFlow<UserDto?> = _me.asStateFlow()

    fun set(user: UserDto) {
        _me.value = user
    }

    fun clear() {
        _me.value = null
    }
}
