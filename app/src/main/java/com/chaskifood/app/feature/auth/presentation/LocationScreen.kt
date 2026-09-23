package com.chaskifood.app.feature.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiBackground

@Composable
fun LocationScreen(
    modifier: Modifier = Modifier,
    onUseCurrentLocation: () -> Unit = {},
    onEnterNewAddress: () -> Unit = {},
    onBack: () -> Unit = {},
) {
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
            title = "Encuentra restaurantes cerca de ti",
            text = "Simplemente ingresa tu ubicación y explora una lista de restaurantes mejor valorados en la zona.",
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AuthSubmitButton(
                text = "Usar mi ubicación actual",
                onClick = onUseCurrentLocation,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onEnterNewAddress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Color(0xFF424242)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF424242),
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 48.dp,
                    vertical = 16.dp,
                ),
            ) {
                Text(
                    text = "Ingresar una nueva dirección",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.84.sp,
                    color = Color(0xFF424242),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}