package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens

private val ReferLinkText = Color(0xFF757575)
private val ReferBodyText = Color(0xFF9E9E9E)
private val ReferHeading = Color(0xFF212121)

@Composable
fun ReferFriendScreen(
    onBack: () -> Unit = {},
    onMore: (() -> Unit)? = null,
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground),
    ) {
        ProfileTopBar(
            title = "Invita a un amigo",
            onBack = onBack,
            onMore = onMore,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChaskiDimens.SpacingLg)
                .padding(bottom = ChaskiDimens.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(ChaskiDimens.SpacingMd))
            Icon(
                imageVector = Icons.Filled.Handshake,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = ReferHeading,
            )

            Spacer(Modifier.height(48.dp))
            Text(
                text = "Obtén 20% de descuento al referido",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = ReferHeading,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingMd))
            Text(
                text = "Comparte esta aplicación con tus amigos y obtén ofertas increíbles solo para ti. Copia el enlace para compartir.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ReferBodyText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))
            Text(
                text = "https://chaskifood.com/refer",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ReferLinkText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingXl))
            Column(
                modifier = Modifier
                    .clickable(onClick = onCopy)
                    .padding(ChaskiDimens.SpacingSm),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copiar enlace",
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF000000),
                )
            }
        }
    }
}