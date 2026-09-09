package com.notifyshare.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Recuperacao de senha em duas etapas: pede o e-mail, depois pede o codigo de
 * 6 digitos (que chegou por e-mail) + a senha nova. `onDone` volta pro login.
 */
@Composable
fun RecoverPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.resetRecoverFlow()
        onDispose { viewModel.resetRecoverFlow() }
    }
    LaunchedEffect(Unit) { viewModel.loadRecoverInfo() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Voltar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        Text("Recuperar a senha", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        when {
            state.recoverDone -> Done(onDone)
            state.recoverStage == RecoverStage.EMAIL -> EmailStep(viewModel, state)
            else -> CodeStep(viewModel, state)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EmailStep(vm: AuthViewModel, state: AuthUiState) {
    Text(
        "Digite o e-mail da sua conta. Se existir, mandamos um código de 6 dígitos.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.recoverInfo?.let { i ->
        Spacer(Modifier.height(8.dp))
        Text(
            "Você pode pedir até ${i.maxRequests} códigos a cada ${i.requestWindowMinutes} min " +
                "(um novo a cada ${i.resendCooldownSeconds}s). Cada código vale ${i.codeTtlMinutes} min. " +
                "Passando disso, a mensagem diz quanto tempo falta para liberar.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuthTextField(
            value = state.recoverEmail,
            onValueChange = vm::onRecoverEmailChange,
            label = "E-mail",
            keyboardType = KeyboardType.Email,
        )
        if (state.errorMessage != null) FormErrorBanner(state.errorMessage)

        PrimaryButton(
            text = "Enviar código",
            onClick = vm::requestRecoverCode,
            enabled = state.canRequestRecover,
            loading = state.recoverBusy,
        )
    }
}

@Composable
private fun CodeStep(vm: AuthViewModel, state: AuthUiState) {
    Text(
        "Enviamos um código para ${state.recoverEmail}. Digite ele e escolha uma senha nova.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.recoverInfo?.let { i ->
        Spacer(Modifier.height(8.dp))
        Text(
            "Você pode errar o código até ${i.maxCodeAttempts} vezes. Depois disso, a recuperação " +
                "fica bloqueada por ${i.lockoutMinutes} min — mas o login com a sua senha atual " +
                "continua funcionando normalmente.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuthTextField(
            value = state.recoverCode,
            onValueChange = vm::onRecoverCodeChange,
            label = "Código de 6 dígitos",
            keyboardType = KeyboardType.NumberPassword,
        )
        AuthTextField(
            value = state.recoverNewPassword,
            onValueChange = vm::onRecoverNewPasswordChange,
            label = "Senha nova",
            isPassword = true,
            supportingText = "Mínimo de 8 caracteres.",
            errorField = state.errorField,
            fieldName = "password",
            errorMessage = state.errorMessage,
        )
        if (state.errorMessage != null && state.errorField == null) FormErrorBanner(state.errorMessage)

        PrimaryButton(
            text = "Redefinir senha",
            onClick = vm::submitRecover,
            enabled = state.canSubmitRecover,
            loading = state.recoverBusy,
        )
        TextButton(onClick = vm::requestRecoverCode, enabled = !state.recoverBusy) {
            Text("Reenviar código", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Done(onDone: () -> Unit) {
    Text(
        "Senha redefinida. Você já pode entrar com a nova senha — as sessões antigas foram encerradas.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(text = "Ir para o login", onClick = onDone, enabled = true, loading = false)
}
