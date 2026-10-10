package com.chaskifood.app.feature.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chaskifood.app.R
import com.chaskifood.app.ui.components.PendingFeatureScreen

@Composable
fun LinkSocialAccountsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PendingFeatureScreen(title = "Vincular cuentas sociales", description = stringResource(R.string.social_pending_description),
        onBack = onBack, modifier = modifier)
}
