package com.notifyshare.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifyshare.auth.GoogleSignInResult
import com.notifyshare.ui.theme.NotifyIcons
import kotlinx.coroutines.launch

/** Campo do formulario que sabe se ele mesmo foi o culpado pelo erro do backend. */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorField: String? = null,
    fieldName: String? = null,
    errorMessage: String? = null,
    contentType: ContentType? = null,
) {
    val isThisFieldWrong = fieldName != null && fieldName == errorField

    // Estado puramente visual, entao mora aqui e nao no ViewModel. rememberSaveable
    // para a senha nao voltar a ficar escondida se a tela girar.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isThisFieldWrong,
        supportingText = when {
            isThisFieldWrong && errorMessage != null -> {
                { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
            }
            supportingText != null -> {
                { Text(supportingText) }
            }
            else -> null
        },
        visualTransformation =
            if (isPassword && !passwordVisible) PasswordVisualTransformation()
            else VisualTransformation.None,
        trailingIcon = if (!isPassword) null else {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) NotifyIcons.EyeOff else NotifyIcons.Eye,
                        // O leitor de tela anuncia a ACAO do botao, nao o estado atual.
                        contentDescription =
                            if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        // Nickname e e-mail sao minusculos no servidor. Sem desligar a
        // capitalizacao e o corretor, o teclado "conserta" o que o usuario digita
        // e ele ve um valor que nao e o que foi gravado.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentType != null) {
                    Modifier.semantics { this.contentType = contentType }
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Botao "Continuar com Google": abre o seletor de contas e entrega o token ao
 * ViewModel. Se o Credential Manager nao achar nenhuma conta no aparelho, cai
 * sozinho no login pelo navegador ([com.notifyshare.auth.GoogleWebAuth]) —
 * sem mostrar erro, sem exigir um segundo toque.
 */
@Composable
fun GoogleSignInButton(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signIn = remember { com.notifyshare.auth.GoogleSignIn(context) }
    val webAuth = remember { com.notifyshare.auth.GoogleWebAuth(context) }
    DisposableEffect(webAuth) { onDispose { webAuth.dispose() } }
    if (!signIn.available) return

    val state by viewModel.state.collectAsStateWithLifecycle()

    val webAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = webAuth.handleResult(data)) {
                is GoogleSignInResult.Token -> viewModel.onGoogleToken(r.idToken)
                is GoogleSignInResult.Error -> viewModel.showError(r.message)
                else -> Unit
            }
        }
    }

    OutlinedButton(
        onClick = {
            scope.launch {
                when (val r = signIn.requestIdToken()) {
                    is GoogleSignInResult.Token -> viewModel.onGoogleToken(r.idToken)
                    GoogleSignInResult.NoAccountOnDevice -> {
                        if (webAuth.available) {
                            webAuthLauncher.launch(webAuth.buildAuthIntent())
                        } else {
                            viewModel.showError("Nenhuma conta Google no aparelho. Adicione uma nas Configurações.")
                        }
                    }
                    is GoogleSignInResult.Error -> viewModel.showError(r.message)
                    GoogleSignInResult.Cancelled -> Unit
                }
            }
        },
        enabled = !state.googleLoading && !state.loading,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (state.googleLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Continuar com Google", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Pergunta o nickname na primeira vez que uma conta Google entra. */
@Composable
fun GoogleNicknameDialog(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (!state.needsGoogleNickname) return

    AlertDialog(
        onDismissRequest = viewModel::cancelGoogleSignup,
        title = { Text("Escolha um nickname") },
        text = {
            Column {
                Text(
                    "Primeira vez com ${state.googleEmail ?: "essa conta"}. " +
                        "O nickname e como as pessoas te encontram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AuthTextField(
                    value = state.googleNickname,
                    onValueChange = viewModel::onGoogleNicknameChange,
                    label = "Nickname",
                    errorField = state.errorField,
                    fieldName = "nickname",
                    errorMessage = state.errorMessage,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    androidx.compose.material3.Checkbox(
                        checked = state.acceptedPrivacy,
                        onCheckedChange = viewModel::onAcceptPrivacyChange,
                    )
                    Column {
                        Text(
                            "Aceito a Política de Privacidade",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { com.notifyshare.ui.common.openUrl(context, com.notifyshare.ui.common.PRIVACY_POLICY_URL) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text("Ver Política de Privacidade", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::completeGoogleSignup,
                enabled = state.canCompleteGoogle,
            ) { Text("Criar conta") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelGoogleSignup) { Text("Cancelar") }
        },
    )
}

/** Erro que nao pertence a um campo — credencial invalida, servidor fora do ar. */
@Composable
fun FormErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
