package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.ChaskiTextField
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiSurface

@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAddCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        bottomBar = {
            ChaskiFlowBottomBar(
                selected = FlowTab.Home,
                onTabClick = { if (it == FlowTab.Home) onHome() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ChaskiAppBar(
                title = "Agregar tarjeta",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Más",
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
            ) {
                ChaskiTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre en la tarjeta",
                )
                ChaskiTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    placeholder = "Número de tarjeta",
                )
                ChaskiTextField(
                    value = cvv,
                    onValueChange = { cvv = it },
                    placeholder = "CVV",
                )
                ChaskiTextField(
                    value = expiry,
                    onValueChange = { expiry = it },
                    placeholder = "Vencimiento",
                )
            }
            ChaskiButton(
                text = "AGREGAR TARJETA",
                onClick = onAddCard,
                variant = ChaskiButtonVariant.Primary,
                modifier = Modifier
                    .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingLg),
            )
        }
    }
}