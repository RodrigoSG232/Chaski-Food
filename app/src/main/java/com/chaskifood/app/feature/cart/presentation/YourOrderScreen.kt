package com.chaskifood.app.feature.cart.presentation

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiSurface

private data class OrderLine(val name: String, val description: String, val quantity: Int, val price: String)

private val orderLines = listOf(
    OrderLine("Chapati con res", "Pan plano, suele servirse con guisos o curris.", 2, "$1.50"),
    OrderLine("Combo de hamburguesa", "Pan plano, suele servirse con guisos o curris.", 2, "$1.50"),
    OrderLine("Plato de ostras", "Pan plano, suele servirse con guisos o curris.", 3, "$1.50"),
)

@Composable
fun YourOrderScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onCheckout: () -> Unit,
    onAddMoreItems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var promoCode by remember { mutableStateOf("") }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXl),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = "Tu pedido",
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

            items(orderLines, key = { it.name }) { line ->
                OrderLineCard(line = line)
            }

            item(key = "promo-title") {
                Text(
                    text = "Agregar código promo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF424242),
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        end = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                    ),
                )
            }

            item(key = "promo-input") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .padding(top = ChaskiDimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFEEEEEE),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        OutlinedTextField(
                            value = promoCode,
                            onValueChange = { promoCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "JFK25NJ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF616161),
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Mail,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF616161),
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    OutlinedButton(
                        onClick = { /* Promo aplicada */ },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF424242)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF424242)),
                    ) {
                        Text(
                            text = "Aplicar promo",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item(key = "summary-title") {
                Text(
                    text = "Resumen del pedido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF010F07),
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        end = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                        bottom = ChaskiDimens.SpacingSm,
                    ),
                )
            }

            item(key = "summary") {
                Column {
                    SummaryRow(label = "Subtotal", value = "$34.50", bold = false)
                    SummaryRow(label = "Costo de envío", value = "$0", bold = false)
                    SummaryRow(label = "Promo", value = "-$2.0", bold = false)
                    SummaryRow(label = "Total", value = "$32.50", bold = true)
                }
            }

            item(key = "add-more") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingLg)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChaskiSurface)
                        .clickable(onClick = onAddMoreItems)
                        .padding(vertical = ChaskiDimens.SpacingLg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Añadir más productos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF757575),
                    )
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF757575),
                    )
                }
            }

            item(key = "checkout") {
                ChaskiButton(
                    text = "CONTINUAR AL PAGO",
                    onClick = onCheckout,
                    variant = ChaskiButtonVariant.Primary,
                    modifier = Modifier
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .padding(bottom = ChaskiDimens.SpacingLg),
                )
            }
        }
    }
}

@Composable
private fun OrderLineCard(line: OrderLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChaskiSurface)
            .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = line.quantity.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (line.quantity == 2) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF424242),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = line.price,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ChaskiPrice,
                )
            }
            Text(
                text = line.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF616161),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    bold: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChaskiSurface)
            .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) Color.Black else Color(0xFF616161),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) Color.Black else Color(0xFF616161),
        )
    }
}