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
    /** Aceite obrigatório da Política de Privacidade no cadastro. */
    val acceptedPrivacy: Boolean = false,
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

    // --- recuperacao de senha ---
    val recoverStage: RecoverStage = RecoverStage.EMAIL,
    val recoverEmail: String = "",
    val recoverCode: String = "",
    val recoverNewPassword: String = "",
    val recoverBusy: Boolean = false,
    val recoverDone: Boolean = false,
    /** Limites da recuperacao (para a tela explicar antes do erro). */
    val recoverInfo: com.notifyshare.data.remote.PasswordResetInfoDto? = null,
) {

    val canRequestRecover: Boolean get() = recoverEmail.isNotBlank() && !recoverBusy
    val canSubmitRecover: Boolean
        get() = recoverCode.trim().length >= 4 && recoverNewPassword.length >= 8 && !recoverBusy
    val canSubmitLogin: Boolean
        get() = identifier.isNotBlank() && password.isNotBlank() && !loading

    val canSubmitRegister: Boolean
        get() = nickname.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
            acceptedPrivacy && !loading

    val canCompleteGoogle: Boolean
        get() = googleNickname.isNotBlank() && acceptedPrivacy && !googleLoading

    val needsGoogleNickname: Boolean get() = pendingGoogleToken != null
}

enum class RecoverStage { EMAIL, CODE }

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onIdentifierChange(value: String) = clearErrorAnd { it.copy(identifier = value) }
    fun onNicknameChange(value: String) = clearErrorAnd { it.copy(nickname = value.lowercase()) }
    fun onEmailChange(value: String) = clearErrorAnd { it.copy(email = value.lowercase()) }
    fun onPasswordChange(value: String) = clearErrorAnd { it.copy(password = value) }
    fun onAcceptPrivacyChange(value: Boolean) = clearErrorAnd { it.copy(acceptedPrivacy = value) }

    fun login() = submit {
        repository.login(_state.value.identifier.trim(), _state.value.password)
    }

    fun register() = submit {
        repository.register(
            nickname = _state.value.nickname.trim(),
            email = _state.value.email.trim(),
            password = _state.value.password,
            acceptedPrivacy = _state.value.acceptedPrivacy,
        )
    }

    // --- login com Google -------------------------------------------------

    fun showError(message: String) = _state.update { it.copy(errorMessage = message) }

    /** Recebe o ID token do seletor de contas. */
    fun onGoogleToken(idToken: String) {
        if (_state.value.googleLoading) return
        _state.update { it.copy(googleLoading = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            // conta existente entra na hora; conta nova cai em needs_nickname e o
            // aceite da privacidade e pedido antes de completar
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
        if (nickname.isBlank() || !_state.value.acceptedPrivacy || _state.value.googleLoading) return
        _state.update { it.copy(googleLoading = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            when (val result = repository.loginWithGoogle(token, nickname, acceptedPrivacy = true)) {
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

    // --- recuperacao de senha -------------------------------------------

    /** Busca os limites (quantos códigos, quanto bloqueia, qual janela) para a tela mostrar. */
    fun loadRecoverInfo() {
        if (_state.value.recoverInfo != null) return
        viewModelScope.launch {
            repository.passwordResetInfo()?.let { info -> _state.update { it.copy(recoverInfo = info) } }
        }
    }

    fun onRecoverEmailChange(v: String) = clearErrorAnd { it.copy(recoverEmail = v.lowercase()) }
    fun onRecoverCodeChange(v: String) = clearErrorAnd { it.copy(recoverCode = v.filter(Char::isDigit).take(6)) }
    fun onRecoverNewPasswordChange(v: String) = clearErrorAnd { it.copy(recoverNewPassword = v) }

    /** Pede o codigo. O backend responde 202 exista a conta ou nao, entao avancamos
     *  para a tela do codigo de qualquer jeito — so nao avanca se a chamada falhou
     *  por rede/limite. */
    fun requestRecoverCode() {
        if (_state.value.recoverBusy || _state.value.recoverEmail.isBlank()) return
        _state.update { it.copy(recoverBusy = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            when (val r = repository.forgotPassword(_state.value.recoverEmail.trim())) {
                is ApiResult.Ok -> _state.update {
                    it.copy(recoverBusy = false, recoverStage = RecoverStage.CODE)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(recoverBusy = false, errorMessage = r.withAttemptsHint())
                }
            }
        }
    }

    fun submitRecover() {
        if (!_state.value.canSubmitRecover) return
        _state.update { it.copy(recoverBusy = true, errorMessage = null, errorField = null) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = repository.resetPassword(s.recoverEmail.trim(), s.recoverCode.trim(), s.recoverNewPassword)) {
                is ApiResult.Ok -> _state.update { it.copy(recoverBusy = false, recoverDone = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        recoverBusy = false,
                        errorMessage = r.withAttemptsHint(),
                        errorField = if (r.code == "weak_password") "password" else null,
                    )
                }
            }
        }
    }

    /** Volta pro comeco do fluxo (ao abrir a tela ou ao sair). */
    fun resetRecoverFlow() = _state.update {
        it.copy(
            recoverStage = RecoverStage.EMAIL, recoverEmail = "", recoverCode = "",
            recoverNewPassword = "", recoverBusy = false, recoverDone = false,
            errorMessage = null, errorField = null,
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
                    // A navegacao normal reage a hasSession; authComplete cobre o
                    // caso "adicionar conta" (hasSession ja era true).
                    _state.update { it.copy(loading = false, authComplete = true) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = result.withAttemptsHint(),
                        errorField = result.field,
                    )
                }
            }
        }
    }

    /**
     * Enriquece o erro do login: "Restam N tentativas antes do bloqueio
     * temporário" enquanto ainda dá pra tentar, e "Tente de novo em Xmin"
     * quando o backend já bloqueou (account_locked traz retryAfterSeconds).
     */
    private fun ApiResult.Failure.withAttemptsHint(): String {
        // Recuperacao de senha e rate limit: o backend ja devolve a frase inteira
        // (tempo restante, tentativas, duracao do bloqueio) — nao acrescenta nada.
        if (code == "rate_limited" || code == "reset_locked" || code == "invalid_reset_code") {
            return message
        }
        retryAfterSeconds?.let { secs ->
            val mins = ((secs + 59) / 60).toInt()
            val quando = if (mins <= 1) "cerca de 1 minuto" else "cerca de $mins minutos"
            return "$message Tente de novo em $quando."
        }
        val remaining = attemptsRemaining ?: return message
        val suffix = if (remaining == 1) "Resta 1 tentativa antes do bloqueio temporário."
        else "Restam $remaining tentativas antes do bloqueio temporário."
        return "$message $suffix"
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
