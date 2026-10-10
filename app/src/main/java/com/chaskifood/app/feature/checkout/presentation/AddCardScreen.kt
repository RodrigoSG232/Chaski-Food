package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chaskifood.app.R
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.components.PendingFeatureScreen

@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PendingFeatureScreen(title = "Agregar tarjeta",
        description = stringResource(R.string.card_pending_description), onBack = onBack, modifier = modifier,
        bottomBar = { ChaskiFlowBottomBar(selected = FlowTab.Home, onTabClick = onTabSelected) })
}
