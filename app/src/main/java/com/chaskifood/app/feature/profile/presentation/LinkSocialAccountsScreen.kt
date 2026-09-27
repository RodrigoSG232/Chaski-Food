package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens

private val SocialBorder = Color(0xFF0C9F3E)
private val SocialButtonText = Color(0xFF0B0B0B)
private val SocialBody = Color(0xFF8D8D8D)

@Composable
fun LinkSocialAccountsScreen(
    onBack: () -> Unit = {},
    onLink: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground),
    ) {
        ProfileTopBar(
            title = "Vincular cuentas sociales",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChaskiDimens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(ChaskiDimens.SpacingXl))
            Text(
                text = "Agrega tus cuentas sociales para mayor seguridad. Irás directamente a su sitio.",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = SocialBody,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))

            SocialLinkButton(
                label = "Facebook",
                onClick = { onLink("Facebook") },
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingLg))
            SocialLinkButton(
                label = "Google",
                onClick = { onLink("Google") },
            )
        }
    }
}

@Composable
private fun SocialLinkButton(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(48.dp))
            .border(2.dp, SocialBorder, RoundedCornerShape(48.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SocialButtonText,
        )
    }
}