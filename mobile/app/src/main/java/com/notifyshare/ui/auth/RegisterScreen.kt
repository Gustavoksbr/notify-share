package com.notifyshare.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.ui.common.verticalScrollbar

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onGoToLogin: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        Text("Criar conta", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Seu nickname e como as pessoas te encontram",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AuthTextField(
                value = state.nickname,
                onValueChange = viewModel::onNicknameChange,
                label = "Nickname",
                supportingText = "Letras, numeros, ponto e underscore",
                errorField = state.errorField,
                fieldName = "nickname",
                errorMessage = state.errorMessage,
            )

            AuthTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "E-mail",
                keyboardType = KeyboardType.Email,
                errorField = state.errorField,
                fieldName = "email",
                errorMessage = state.errorMessage,
            )

            AuthTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Senha",
                supportingText = "Minimo de 8 caracteres",
                isPassword = true,
                errorField = state.errorField,
                fieldName = "password",
                errorMessage = state.errorMessage,
            )

            if (state.errorMessage != null && state.errorField == null) {
                FormErrorBanner(state.errorMessage!!)
            }

            EmailNotice()

            PrivacyConsentRow(
                checked = state.acceptedPrivacy,
                onCheckedChange = viewModel::onAcceptPrivacyChange,
                onOpenPolicy = onOpenPrivacy,
            )

            PrimaryButton(
                text = "Criar conta",
                onClick = viewModel::register,
                enabled = state.canSubmitRegister,
                loading = state.loading,
            )

            Text(
                "ou",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            GoogleSignInButton(viewModel)

            TextButton(
                onClick = {
                    viewModel.resetForm()
                    onGoToLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ja tenho conta", color = MaterialTheme.colorScheme.primary)
            }
        }

        GoogleNicknameDialog(viewModel)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacyConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenPolicy: () -> Unit,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text(
                "Li e aceito a Política de Privacidade. Minhas notificações são processadas " +
                    "no meu aparelho; só é compartilhado o que minhas regras definem, com quem " +
                    "eu escolher. O histórico compartilhado é apagado após 7 dias.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPolicy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Ver Política de Privacidade", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** O aviso do ADR-001: o e-mail existe, mas nao e verificado e nao recebe nada. */
@Composable
private fun EmailNotice() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row {
            Text(
                text = "Sobre o e-mail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = "Você pode usar o seu email para recuperação de senha. Seu email não é compartilhado publicamente.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
