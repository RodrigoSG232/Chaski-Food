package com.chaskifood.app.feature.auth.presentation

import androidx.activity.compose.LocalActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun VerifyPhoneScreen(
    modifier: Modifier = Modifier,
    onVerify: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PhoneAuthViewModel = hiltViewModel(),
) {
    val smsCode by viewModel.smsCode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val phoneVerified by viewModel.phoneVerified.collectAsState()
    val resendSeconds by viewModel.resendSeconds.collectAsState()
    val smsNotice by viewModel.smsNotice.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val activity = LocalActivity.current
    var continued by remember { mutableStateOf(false) }
    val continueOnce: () -> Unit = { if (!continued) { continued = true; onVerify() } }
    LaunchedEffect(phoneVerified) { if (phoneVerified) continueOnce() }

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
            title = "Verifica tu número de teléfono",
            text = "Ingresa el código de 6 dígitos que enviamos por SMS a $phoneNumber.",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AuthTextField(
                value = smsCode,
                onValueChange = { viewModel.smsCode.value = it },
                label = "Código SMS (6 dígitos)",
                placeholder = "123456",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(30.dp))

            smsNotice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            TextButton(
                enabled = resendSeconds == 0 && uiState !is UiState.Loading && activity != null,
                onClick = { activity?.let { viewModel.resendCode(it, continueOnce) } },
            ) {
                Text(if (resendSeconds > 0) "Reenviar SMS en ${resendSeconds}s" else "Reenviar código SMS")
            }
            TextButton(enabled = uiState !is UiState.Loading, onClick = onBack) {
                Text("Corregir número de teléfono")
            }

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Código SMS incorrecto",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                AuthSubmitButton(
                    text = "REGISTRARME",
                    onClick = { viewModel.verifyCode(onSuccess = continueOnce) },
                    containerColor = ChaskiTextPrimary,
                )
            }

            Spacer(Modifier.height(32.dp))

            AuthTermsFooter()

            Spacer(Modifier.height(32.dp))
        }
    }
}
