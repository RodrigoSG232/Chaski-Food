package com.chaskifood.app.feature.main.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chaskifood.app.ui.components.EmptyState
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingLg,
            ),
        )
        EmptyState(message = message)
    }
}