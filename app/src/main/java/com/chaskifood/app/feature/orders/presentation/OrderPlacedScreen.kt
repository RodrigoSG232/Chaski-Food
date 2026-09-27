package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiSurface

@Composable
fun OrderPlacedScreen(
    onTrackOrder: () -> Unit,
    onAddMoreOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        bottomBar = {
            ChaskiFlowBottomBar(selected = FlowTab.Search)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChaskiAppBar(
                title = "",
                onBackClick = onTrackOrder,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Más",
                        )
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "¡Pedido realizado!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
            )
            Spacer(Modifier.size(ChaskiDimens.SpacingMd))
            Text(
                text = "Realizaste tu pedido correctamente.\nAsignaremos un repartidor para que lo\nrecogerá y te avisaremos con los detalles.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF616161),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(ChaskiDimens.SpacingXxl))
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(98.dp),
                tint = Color(0xFF616161),
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChaskiDimens.ScreenPadding)
                    .padding(bottom = ChaskiDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
            ) {
                ChaskiButton(
                    text = "Rastrear pedido",
                    onClick = onTrackOrder,
                    variant = ChaskiButtonVariant.Primary,
                )
                OutlinedButton(
                    onClick = onAddMoreOrders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ChaskiDimens.SpacingSm),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Color(0xFF212121)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF212121)),
                ) {
                    Text(
                        text = "Añadir más pedidos",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}