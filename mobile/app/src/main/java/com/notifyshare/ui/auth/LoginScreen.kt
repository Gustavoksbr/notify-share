package com.notifyshare.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.ui.common.verticalScrollbar

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
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
        if (onCancel != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(40.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BrandMark()
            Text(
                if (onCancel != null) "Adicionar conta" else "Notify Share",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Escolha o que o seu celular avisa\ne para quem",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthTextField(
                value = state.identifier,
                onValueChange = viewModel::onIdentifierChange,
                label = "Nickname ou e-mail",
                errorField = state.errorField,
                fieldName = "identifier",
                errorMessage = state.errorMessage,
            )

            AuthTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Senha",
                isPassword = true,
                errorField = state.errorField,
                fieldName = "password",
                errorMessage = state.errorMessage,
            )

            // "Esqueci a senha" logo abaixo do campo, alinhado à direita — ocupa
            // pouca altura, para o botão do Google caber sem rolar a tela.
            Text(
                "Esqueci minha senha",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onForgotPassword)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            )

            // Erro sem campo associado: credencial invalida, servidor fora.
            if (state.errorMessage != null && state.errorField == null) {
                FormErrorBanner(state.errorMessage!!)
            }

            PrimaryButton(
                text = "Entrar",
                onClick = viewModel::login,
                enabled = state.canSubmitLogin,
                loading = state.loading,
            )

            Text(
                "ou",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            GoogleSignInButton(viewModel)

            TextButton(
                onClick = {
                    viewModel.resetForm()
                    onGoToRegister()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Criar conta com e-mail e senha", color = MaterialTheme.colorScheme.primary)
            }
        }

        GoogleNicknameDialog(viewModel)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BrandMark() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                id = com.notifyshare.R.drawable.ic_brand
            ),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
    }
}
