package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.R
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.auth.presentation.AuthSubmitButton
import com.chaskifood.app.feature.auth.presentation.AuthTextField
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiStatusObservedBg
import com.chaskifood.app.ui.theme.ChaskiStatusObservedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun RegisterBusinessScreen(
    onSubmitted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    observations: String? = null,
    isResubmission: Boolean = false,
    viewModel: RegisterBusinessViewModel = hiltViewModel(),
) {
    val name by viewModel.businessName.collectAsState()
    val ruc by viewModel.ruc.collectAsState()
    val address by viewModel.legalAddress.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val email by viewModel.email.collectAsState()
    val category by viewModel.category.collectAsState()
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChaskiDimens.ScreenPadding),
        ) {
            Text(
                text = if (isResubmission) "Corregir Solicitud" else stringResource(R.string.register_business_screen_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = stringResource(R.string.register_business_screen_subtitle),
                fontSize = 14.sp,
                color = ChaskiTextMuted,
            )

            if (!observations.isNullOrBlank()) {
                Spacer(Modifier.height(ChaskiDimens.SpacingLg))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ChaskiStatusObservedBg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(ChaskiDimens.SpacingMd)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = ChaskiStatusObservedFg,
                            )
                            Text(
                                text = "Observaciones del Administrador",
                                fontWeight = FontWeight.Bold,
                                color = ChaskiStatusObservedFg,
                                modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                            )
                        }
                        Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                        Text(
                            text = observations,
                            fontSize = 14.sp,
                            color = Color(0xFFBF360C),
                        )
                    }
                }
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingXl))

            AuthTextField(
                value = name,
                onValueChange = { viewModel.businessName.value = it },
                label = "Nombre del negocio / Razón Social",
                placeholder = "Ej. Pollos & Parrillas Chaski",
                leadingIcon = Icons.Filled.Business,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = ruc,
                onValueChange = { viewModel.ruc.value = it.filter { char -> char.isDigit() }.take(11) },
                label = "RUC (11 dígitos)",
                placeholder = "20123456789",
                leadingIcon = Icons.Filled.Numbers,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = address,
                onValueChange = { viewModel.legalAddress.value = it },
                label = "Dirección legal / Sede principal",
                placeholder = "Av. Larco 123, Miraflores, Lima",
                leadingIcon = Icons.Filled.Place,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = phone,
                onValueChange = { viewModel.phone.value = it },
                label = "Teléfono de contacto",
                placeholder = "+51 987654321",
                leadingIcon = Icons.Filled.Phone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = email,
                onValueChange = { viewModel.email.value = it },
                label = "Correo comercial",
                placeholder = "contacto@negocio.com",
                leadingIcon = Icons.Filled.Email,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = category,
                onValueChange = { viewModel.category.value = it },
                label = "Categoría comercial",
                placeholder = "Restaurante / Cafetería / Comida Rápida",
                leadingIcon = Icons.Filled.Category,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingLg))

            if (uiState is UiState.Error) {
                Text(
                    text = (uiState as UiState.Error).message ?: "Error al procesar la solicitud",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = ChaskiDimens.SpacingMd),
                )
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                AuthSubmitButton(
                    text = if (isResubmission) "CORREGIR Y REENVIAR SOLICITUD" else "SOLICITAR HABILITACIÓN",
                    onClick = {
                        viewModel.submitRequest(isResubmission = isResubmission, onSuccess = onSubmitted)
                    },
                )
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))
        }
    }
}