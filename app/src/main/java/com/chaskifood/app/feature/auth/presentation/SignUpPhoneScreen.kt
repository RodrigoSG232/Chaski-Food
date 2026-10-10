package com.chaskifood.app.feature.auth.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.ui.theme.ChaskiBackground

data class CountryCode(
    val name: String,
    val flag: String,
    val dialCode: String,
)

val countries = listOf(
    CountryCode("Perú", "🇵🇪", "+51"),
    CountryCode("México", "🇲🇽", "+52"),
    CountryCode("Colombia", "🇨🇴", "+57"),
    CountryCode("Chile", "🇨🇱", "+56"),
    CountryCode("Argentina", "🇦🇷", "+54"),
    CountryCode("Ecuador", "🇪🇨", "+593"),
    CountryCode("Bolivia", "🇧🇴", "+591"),
    CountryCode("Venezuela", "🇻🇪", "+58"),
    CountryCode("Estados Unidos", "🇺🇸", "+1"),
    CountryCode("España", "🇪🇸", "+34"),
    CountryCode("Brasil", "🇧🇷", "+55"),
)

@Composable
fun SignUpPhoneScreen(
    modifier: Modifier = Modifier,
    onGetCode: (verificationId: String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PhoneAuthViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsState()

    var countryDialCode by rememberSaveable { mutableStateOf(countries.first().dialCode) }
    val selectedCountry = countries.first { it.dialCode == countryDialCode }
    var expanded by remember { mutableStateOf(false) }
    var phoneInput by rememberSaveable { mutableStateOf("") }

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
            title = "Empieza con tu número de teléfono",
            text = "Ingresa tu número de teléfono para usar Chaski Food y disfrutar de tu comida.",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "NÚMERO DE TELÉFONO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = Color(0xFF424242),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    Surface(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEEEEE),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF212121),
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Seleccionar país",
                                tint = Color(0xFF757575),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
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
                                    countryDialCode = country.dialCode
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "987654321",
                            color = Color(0xFF9E9E9E),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
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
                            val fullPhone = selectedCountry.dialCode + phoneInput.trim()
                            viewModel.phoneNumber.value = fullPhone
                            viewModel.sendCode(activity, onSuccess = {
                                val verId = viewModel.verificationId.value ?: ""
                                onGetCode(verId)
                            })
                        }
                    },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
