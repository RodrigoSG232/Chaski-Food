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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary

@Composable
fun ProfileSettingsScreen(
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf("Abel Biwott") }
    var email by remember { mutableStateOf("abel@correo.com") }
    var password by remember { mutableStateOf("") }

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
                .padding(horizontal = ChaskiDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXl),
        ) {
            Spacer(Modifier.height(ChaskiDimens.SpacingSm))
            ProfileField(
                label = "Nombre completo",
                value = name,
                onValueChange = { name = it },
                placeholder = "Abel Biwott",
            )
            ProfileField(
                label = "Correo electrónico",
                value = email,
                onValueChange = { email = it },
                placeholder = "abel@correo.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ProfileField(
                label = "Número de teléfono",
                value = password,
                onValueChange = { password = it },
                placeholder = "********",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                password = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ChaskiDimens.SpacingXl)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChaskiPrimary)
                    .clickable(onClick = onSave)
                    .padding(vertical = ChaskiDimens.SpacingLg, horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "SIGUIENTE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
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
                .background(Color(0xFFEEEEEE))
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
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFF616161)),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF212121),
                ),
                keyboardOptions = keyboardOptions,
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}