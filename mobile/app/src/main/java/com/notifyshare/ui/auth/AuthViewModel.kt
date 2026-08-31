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
) {
    val canSubmitLogin: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !loading

    val canSubmitRegister: Boolean
        get() = nickname.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !loading
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
                    // Nao mexemos no estado: a navegacao reage a hasSession,
                    // que o repositorio ja atualizou ao guardar os tokens.
                    _state.update { it.copy(loading = false) }
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
