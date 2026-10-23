package com.chaskifood.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiAction
import com.chaskifood.app.ui.theme.ChaskiOnPrimary
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiDimens

enum class ChaskiButtonVariant {
    Primary,
    Accent,
    Outlined,
    Text,
}

@Composable
fun ChaskiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ChaskiButtonVariant = ChaskiButtonVariant.Primary,
) {
    val shape = MaterialTheme.shapes.large
    when (variant) {
        ChaskiButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = ChaskiDimens.BottomNavHeight - 12.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChaskiAction,
                contentColor = ChaskiOnPrimary,
            ),
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }

        ChaskiButtonVariant.Accent -> Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = ChaskiDimens.BottomNavHeight - 12.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChaskiPrimary,
                contentColor = ChaskiOnPrimary,
            ),
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }

        ChaskiButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = ChaskiDimens.BottomNavHeight - 12.dp),
            enabled = enabled,
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, ChaskiPrimary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ChaskiPrimary),
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }

        ChaskiButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Text(text = text, color = ChaskiPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}