package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary

@Composable
fun ProfileSettingsScreen(
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var name by rememberSaveable { mutableStateOf("") }
    var draftUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            if (draftUserId != user.uid) {
                name = user.displayName.orEmpty()
                draftUserId = user.uid
            }
            if (!user.email.isNullOrBlank()) {
                email = user.email
            }
            if (!user.phoneNumber.isNullOrBlank()) {
                phone = user.phoneNumber
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground),
    ) {
        ProfileTopBar(
            title = "Ajustes del perfil",
            onBack = onBack,
            onMore = onMore,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ChaskiDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXl),
        ) {
            Spacer(Modifier.height(ChaskiDimens.SpacingSm))

            ProfileField(
                label = "Nombre completo",
                value = name,
                onValueChange = { name = it },
                placeholder = "Tu nombre",
            )
            ProfileField(
                label = "Correo electrónico (Solo lectura)",
                value = email,
                onValueChange = {},
                placeholder = "tu_correo@ejemplo.com",
                readOnly = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ProfileField(
                label = "Número de teléfono (Verificado)",
                value = phone,
                onValueChange = {},
                placeholder = "+51 999 999 999",
                readOnly = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Error al guardar el perfil",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChaskiPrimary)
                        .clickable {
                            viewModel.updateProfile(name, onSuccess = onSave)
                        }
                        .padding(vertical = ChaskiDimens.SpacingLg, horizontal = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "GUARDAR CAMBIOS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.84.sp,
                        color = Color.White,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ChaskiDimens.SpacingXl)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFD32F2F))
                    .clickable {
                        viewModel.signOut {
                            onSignOut()
                        }
                    }
                    .padding(vertical = ChaskiDimens.SpacingLg, horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "CERRAR SESIÓN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.84.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd)) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = Color(0xFF424242),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (readOnly) Color(0xFFE0E0E0) else Color(0xFFEEEEEE))
                .padding(horizontal = ChaskiDimens.SpacingLg, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF616161),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = if (readOnly) { {} } else onValueChange,
                readOnly = readOnly,
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFF616161)),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = if (readOnly) Color(0xFF757575) else Color(0xFF212121),
                ),
                keyboardOptions = keyboardOptions,
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
