package com.chaskifood.app.feature.orders.presentation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun OrderTrackProgressScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF929292)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 187.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .padding(ChaskiDimens.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXl),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 0.dp)
                        .size(width = 43.dp, height = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFD9D9D9)),
                )
            }

            ProgressStep(
                filled = true,
                icon = Icons.Filled.SoupKitchen,
                title = "Preparando",
                description = "Nuestros chefs están preparando tu comida.",
            )
            ProgressStep(
                filled = true,
                icon = Icons.Filled.Archive,
                title = "Empacando",
                description = "¡Todo listo! Vamos a guardarlo todo.",
            )
            DriverProgressStep(
                title = "En camino",
                driverName = "James T.",
                driverRole = "Tu repartidor",
            )
            ProgressStep(
                filled = false,
                icon = Icons.Filled.Person,
                title = "Entregado",
                description = "¡Disfruta tu comida! Gracias a Joy, Patrick y Omosh. Tómate un minuto para calificar nuestro servicio.",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF9E9E9E))
                    .clickable(onClick = onNext)
                    .padding(vertical = ChaskiDimens.SpacingMd, horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Califica nuestro servicio",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.84.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProgressStep(
    filled: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        StepTitleRow(filled = filled, title = title)
        StepDescriptionRow(icon = icon, description = description)
    }
}

@Composable
private fun StepTitleRow(filled: Boolean, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        StepDot(filled = filled)
        Text(
            text = title,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = Color(0xFF212121),
        )
    }
}

@Composable
private fun StepDescriptionRow(icon: ImageVector, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFF757575),
            )
        }
        Text(
            text = description,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF757575),
            modifier = Modifier.widthIn(max = 258.dp),
        )
    }
}

@Composable
private fun DriverProgressStep(
    title: String,
    driverName: String,
    driverRole: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
        StepTitleRow(filled = true, title = title)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF757575),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = driverName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF212121),
                )
                Text(
                    text = driverRole,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575),
                )
            }
            ActionIconButton(icon = Icons.Filled.LocalPhone)
            ActionIconButton(icon = Icons.Filled.Sms)
        }
    }
}

@Composable
private fun ActionIconButton(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAC7C7)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF212121),
        )
    }
}

@Composable
private fun StepDot(filled: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (filled) Color(0xFFDA4242) else Color.Transparent)
            .border(
                width = 1.33.dp,
                color = Color(0xFFDA4242),
                shape = CircleShape,
            ),
    )
}