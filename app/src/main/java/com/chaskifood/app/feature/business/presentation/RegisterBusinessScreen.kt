package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.chaskifood.app.feature.auth.presentation.countries
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
    LaunchedEffect(isResubmission) { if (isResubmission) viewModel.loadExistingForResubmission() }
    val loadedObservations by viewModel.observations.collectAsState()
    val canResubmit by viewModel.readyToResubmit.collectAsState()
    val name by viewModel.businessName.collectAsState()
    val ruc by viewModel.ruc.collectAsState()
    val address by viewModel.legalAddress.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val email by viewModel.email.collectAsState()
    val category by viewModel.category.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val countryDialCode by viewModel.countryDialCode.collectAsState()
    val phoneError by viewModel.phoneError.collectAsState()
    val selectedCountry = countries.first { it.dialCode == countryDialCode }
    var expandedCountryDropdown by remember { mutableStateOf(false) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    val categoryOptions = listOf("Restaurante", "Tienda")

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
                text = if (isResubmission) stringResource(R.string.resubmit_request) else stringResource(R.string.register_business_screen_title),
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

            if (!(loadedObservations ?: observations).isNullOrBlank()) {
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
                                text = stringResource(R.string.admin_observations_title),
                                fontWeight = FontWeight.Bold,
                                color = ChaskiStatusObservedFg,
                                modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                            )
                        }
                        Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                        Text(
                            text = (loadedObservations ?: observations).orEmpty(),
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
                label = stringResource(R.string.business_name_label),
                placeholder = "Ej. Pollos & Parrillas Chaski",
                leadingIcon = Icons.Filled.Business,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = ruc,
                onValueChange = { viewModel.ruc.value = it.filter { char -> char.isDigit() }.take(11) },
                label = stringResource(R.string.ruc_label),
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
                label = stringResource(R.string.legal_address_label),
                placeholder = "Av. Larco 123, Miraflores, Lima",
                leadingIcon = Icons.Filled.Place,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            Text(
                text = stringResource(R.string.phone_label),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242),
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(8.dp))
                            .clickable(enabled = uiState !is UiState.Loading) { expandedCountryDropdown = !expandedCountryDropdown }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF616161),
                        )
                    }

                    DropdownMenu(
                        expanded = expandedCountryDropdown,
                        onDismissRequest = { expandedCountryDropdown = false },
                    ) {
                        countries.forEach { country ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${country.flag} ${country.name} (${country.dialCode})",
                                        fontSize = 14.sp,
                                    )
                                },
                                onClick = {
                                    viewModel.selectPhoneCountry(country.dialCode)
                                    expandedCountryDropdown = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = viewModel::updatePhone,
                    enabled = uiState !is UiState.Loading,
                    isError = phoneError != null,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "987654321",
                            color = Color(0xFF9E9E9E),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEEEEEE),
                        unfocusedContainerColor = Color(0xFFEEEEEE),
                        focusedBorderColor = Color(0xFF388E3C),
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }

            Text(
                text = phoneError ?: "Número nacional, sin el prefijo ${selectedCountry.dialCode}.",
                color = if (phoneError != null) MaterialTheme.colorScheme.error else ChaskiTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            AuthTextField(
                value = email,
                onValueChange = { viewModel.email.value = it },
                label = stringResource(R.string.business_email_label),
                placeholder = "contacto@negocio.com",
                leadingIcon = Icons.Filled.Email,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            Text(
                text = stringResource(R.string.category_label),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242),
            )
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expandedCategoryDropdown = !expandedCategoryDropdown }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF616161),
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Category,
                            contentDescription = null,
                            tint = Color(0xFF757575),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEEEEEE),
                        unfocusedContainerColor = Color(0xFFEEEEEE),
                        focusedBorderColor = Color(0xFF388E3C),
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedCategoryDropdown = !expandedCategoryDropdown },
                )

                DropdownMenu(
                    expanded = expandedCategoryDropdown,
                    onDismissRequest = { expandedCategoryDropdown = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            onClick = {
                                viewModel.category.value = option
                                expandedCategoryDropdown = false
                            },
                        )
                    }
                }
            }

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
                    color = Color(0xFF388E3C),
                )
            } else {
                AuthSubmitButton(
                    text = stringResource(R.string.submit_business_request),
                    onClick = {
                        if (isResubmission && !canResubmit) viewModel.loadExistingForResubmission()
                        else viewModel.submitRequest(
                            isResubmission = isResubmission,
                            onSuccess = onSubmitted,
                        )
                    },
                )
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))
        }
    }
}
