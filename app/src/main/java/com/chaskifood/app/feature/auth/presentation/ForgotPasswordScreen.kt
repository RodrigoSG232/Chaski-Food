package com.chaskifood.app.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun ForgotPasswordScreen(
    modifier: Modifier = Modifier,
    onResetSent: (email: String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val email by viewModel.email.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        AuthTitleBanner(
            title = "Restablecer contraseña",
            text = "Ingresa tu correo electrónico y te enviaremos el enlace de restablecimiento",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AuthTextField(
                value = email,
                onValueChange = { viewModel.email.value = it },
                label = "Correo",
                placeholder = "laura@correo.com",
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Mail,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF9E9E9E),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(20.dp))

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Error al enviar el correo",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                AuthSubmitButton(
                    text = "RESTABLECER CONTRASEÑA",
                    onClick = {
                        viewModel.sendPasswordResetEmail { sentEmail ->
                            onResetSent(sentEmail)
                        }
                    },
                    containerColor = ChaskiTextPrimary,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
