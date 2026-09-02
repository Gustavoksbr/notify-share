package com.notifyshare.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notifyshare.data.ApiResult
import com.notifyshare.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val identifier: String = "",
    val nickname: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
    /** Nome do campo que o backend apontou, para destacar o certo no formulario. */
    val errorField: String? = null,
    /** Virou true logo apos qualquer autenticacao bem-sucedida. */
    val authComplete: Boolean = false,

    // --- login com Google ---
    val googleLoading: Boolean = false,
    /** Preenchido quando o backend pede um nickname (primeira vez desta conta Google). */
    val googleEmail: String? = null,
    val googleNickname: String = "",
    val pendingGoogleToken: String? = null,
) {
    val canSubmitLogin: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !loading

    val canSubmitRegister: Boolean
        get() = nickname.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !loading

    val needsGoogleNickname: Boolean get() = pendingGoogleToken != null
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onIdentifierChange(value: String) = clearErrorAnd { it.copy(identifier = value) }
    fun onNicknameChange(value: String) = clearErrorAnd { it.copy(nickname = value.lowercase()) }
    fun onEmailChange(value: String) = clearErrorAnd { it.copy(email = value.lowercase()) }
    fun onPasswordChange(value: String) = clearErrorAnd { it.copy(password = value) }

    fun login() = submit {
        repository.login(_state.value.identifier.trim(), _state.value.password)
    }

    fun register() = submit {
        repository.register(
            nickname = _state.value.nickname.trim(),
            email = _state.value.email.trim(),
            password = _state.value.password,
        )
    }

    // --- login com Google -------------------------------------------------

    fun showError(message: String) = _state.update { it.copy(errorMessage = message) }

    /** Recebe o ID token do seletor de contas. */
    fun onGoogleToken(idToken: String) {
        if (_state.value.googleLoading) return
        _state.update { it.copy(googleLoading = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            when (val result = repository.loginWithGoogle(idToken)) {
                is ApiResult.Ok -> _state.update { it.copy(googleLoading = false, authComplete = true) }
                is ApiResult.Failure -> _state.update {
                    if (result.code == "needs_nickname") {
                        // message carrega o e-mail da conta Google
                        it.copy(googleLoading = false, pendingGoogleToken = idToken, googleEmail = result.message)
                    } else {
                        it.copy(googleLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun onGoogleNicknameChange(value: String) =
        _state.update { it.copy(googleNickname = value.lowercase(), errorMessage = null, errorField = null) }

    fun completeGoogleSignup() {
        val token = _state.value.pendingGoogleToken ?: return
        val nickname = _state.value.googleNickname.trim()
        if (nickname.isBlank() || _state.value.googleLoading) return
        _state.update { it.copy(googleLoading = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            when (val result = repository.loginWithGoogle(token, nickname)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(googleLoading = false, pendingGoogleToken = null, googleEmail = null, authComplete = true)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(googleLoading = false, errorMessage = result.message, errorField = result.field)
                }
            }
        }
    }

    fun cancelGoogleSignup() = _state.update {
        it.copy(pendingGoogleToken = null, googleEmail = null, googleNickname = "", errorMessage = null)
    }

    /** Limpa o formulario ao trocar de tela, para a senha nao sobrar na memoria. */
    fun resetForm() {
        _state.value = AuthUiState()
    }

    private fun submit(action: suspend () -> ApiResult<*>) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Ok -> {
                    // A navegacao normal reage a hasSession; authComplete cobre o
                    // caso "adicionar conta" (hasSession ja era true).
                    _state.update { it.copy(loading = false, authComplete = true) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = result.message,
                        errorField = result.field,
                    )
                }
            }
        }
    }

    private fun clearErrorAnd(transform: (AuthUiState) -> AuthUiState) {
        _state.update { transform(it).copy(errorMessage = null, errorField = null) }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(repository) as T
    }
}
