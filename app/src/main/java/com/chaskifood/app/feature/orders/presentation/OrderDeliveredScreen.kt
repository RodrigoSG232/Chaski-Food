package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun OrderDeliveredScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF616161))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = ChaskiDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(120.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 328.dp)
                .padding(horizontal = ChaskiDimens.SpacingLg)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
        ) {
            Text(
                text = "¡Pedido entregado!",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color = Color(0xFF212121),
            )
            Text(
                text = "Disfruta tu comida, Janet. No dudes en calificar tu experiencia con el repartidor (Linda) y nuestros servicios.",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 279.dp),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF212121))
                    .clickable(onClick = onNext)
                    .padding(horizontal = ChaskiDimens.SpacingXl, vertical = ChaskiDimens.SpacingSm),
            ) {
                Text(
                    text = "SIGUIENTE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.84.sp,
                    color = Color.White,
                )
            }
        }

    }
}
