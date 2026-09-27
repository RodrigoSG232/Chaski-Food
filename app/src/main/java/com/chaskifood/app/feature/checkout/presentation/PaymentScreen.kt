package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.components.HomeIcons
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private data class PaymentMethod(val name: String, val badge: String)

private val paymentMethods = listOf(
    PaymentMethod("PayPal", "P"),
    PaymentMethod("Venmo", "V"),
    PaymentMethod("Wise", "W"),
    PaymentMethod("●●●● 3856", "MC"),
)

@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onCompletePurchase: () -> Unit,
    onAddCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(3) }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = ChaskiSurface,
        bottomBar = {
            ChaskiFlowBottomBar(
                selected = FlowTab.Home,
                onTabClick = { if (it == FlowTab.Home) onHome() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXl),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = "Pago",
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
            }

            item(key = "total") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .background(Color(0xFFFBE9E9), RoundedCornerShape(8.dp))
                        .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = HomeIcons.MapPin,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    Text(
                        text = "Total a pagar $40.00",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF616161),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            items(paymentMethods, key = { it.name }) { method ->
                val index = paymentMethods.indexOf(method)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChaskiSurface)
                        .clickable { selected = index }
                        .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PaymentLogo(badge = method.badge)
                        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
                        Text(
                            text = method.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF424242),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (selected == index) {
                            Icons.Filled.RadioButtonChecked
                        } else {
                            Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected == index) ChaskiPrice else ChaskiTextMuted,
                    )
                }
            }

            item(key = "new-card") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChaskiSurface)
                        .clickable(onClick = onAddCard)
                        .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFFBFBFB), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF424242),
                        )
                    }
                    Spacer(Modifier.width(ChaskiDimens.SpacingLg))
                    Text(
                        text = "Nueva tarjeta",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF424242),
                    )
                }
            }

            item(key = "purchase") {
                ChaskiButton(
                    text = "COMPLETAR COMPRA",
                    onClick = onCompletePurchase,
                    variant = ChaskiButtonVariant.Primary,
                    modifier = Modifier
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .padding(vertical = ChaskiDimens.SpacingLg),
                )
            }
        }
    }
}

@Composable
private fun PaymentLogo(badge: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(0xFFFBFBFB), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF113984),
        )
    }
}