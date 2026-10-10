package com.chaskifood.app.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun CheckEmailScreen(
    email: String,
    modifier: Modifier = Modifier,
    onResend: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        AuthTitleBanner(
            title = "Revisa tu correo",
            text = "Hemos enviado un correo con instrucciones a $email.",
            linkText = "¿Tienes algún problema?",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if ((uiState as? UiState.Success)?.data == Unit) {
                Text("Si el correo está registrado, recibirás un nuevo enlace de recuperación.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Error al reenviar el correo",
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
                    text = "REENVIAR ENLACE",
                    onClick = {
                        if (email.isNotBlank()) {
                            viewModel.email.value = email
                            viewModel.sendPasswordResetEmail {
                                onResend()
                            }
                        } else {
                            onResend()
                        }
                    },
                    containerColor = ChaskiTextPrimary,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
