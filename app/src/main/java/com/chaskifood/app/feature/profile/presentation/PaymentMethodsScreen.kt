package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary

private data class PaymentMethod(
    val name: String,
    val icon: ImageVector,
)

private val paymentMethods = listOf(
    PaymentMethod("PayPal", Icons.Filled.Payments),
    PaymentMethod("M-Pesa", Icons.Filled.PhoneAndroid),
    PaymentMethod("Visa", Icons.Filled.CreditCard),
    PaymentMethod("Contra entrega", Icons.Filled.AccountBalanceWallet),
)

@Composable
fun PaymentMethodsScreen(
    onNext: () -> Unit,
    onAddCard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(3) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color(0xFF0D0D0D),
                )
            }
            Text(
                text = "Métodos de pago",
                fontSize = 24.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF0D0D0D),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Más",
                    tint = Color(0xFF0D0D0D),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = ChaskiDimens.SpacingMd),
        ) {
            paymentMethods.forEachIndexed { index, method ->
                PaymentTile(
                    method = method,
                    selected = index == selected,
                    onClick = { selected = index },
                )
            }

            NewCardTile(onClick = onAddCard)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChaskiDimens.SpacingLg)
                    .padding(bottom = ChaskiDimens.SpacingXl)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChaskiPrimary)
                    .clickable(onClick = onNext)
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
private fun PaymentTile(
    method: PaymentMethod,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaymentLogo(icon = method.icon)
        Text(
            text = method.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = Color(0xFF424242),
            modifier = Modifier
                .weight(1f)
                .padding(start = ChaskiDimens.SpacingLg),
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = ChaskiPrimary,
                unselectedColor = Color(0xFF9E9E9E),
            ),
        )
    }
}

@Composable
private fun PaymentLogo(icon: ImageVector) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.25.dp, Color(0xFFDBDBDB), RoundedCornerShape(5.dp))
            .background(Color(0xFFFBFBFB))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF424242),
        )
    }
}

@Composable
private fun NewCardTile(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaymentLogo(icon = Icons.Filled.Add)
        Text(
            text = "Nueva tarjeta",
            fontSize = 16.sp,
            color = Color(0xFF424242),
            modifier = Modifier.padding(start = ChaskiDimens.SpacingLg),
        )
        Spacer(Modifier.size(48.dp))
    }
}