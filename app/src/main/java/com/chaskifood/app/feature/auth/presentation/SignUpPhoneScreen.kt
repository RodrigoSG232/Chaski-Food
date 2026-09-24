package com.chaskifood.app.feature.auth.presentation

import android.app.Activity
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground

@Composable
fun SignUpPhoneScreen(
    modifier: Modifier = Modifier,
    onGetCode: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PhoneAuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val phone by viewModel.phoneNumber.collectAsState()
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
            title = "Empieza con tu número de teléfono",
            text = "Ingresa tu número de teléfono para usar Chaski Food y disfrutar de tu comida.",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AuthTextField(
                value = phone,
                onValueChange = { viewModel.phoneNumber.value = it },
                label = "Número de teléfono",
                placeholder = "+51 999 999 999",
                leadingIcon = Icons.Filled.Phone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(30.dp))

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Error al enviar el código",
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
                    text = "ENVIAR CÓDIGO",
                    onClick = {
                        if (activity != null) {
                            viewModel.sendCode(activity, onSuccess = onGetCode)
                        }
                    },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}