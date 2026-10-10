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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.chaskifood.app.ui.theme.ChaskiSecondary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private enum class DeliveryMethod { PickUp, Deliver }

@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    onContinueToPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var method by remember { mutableStateOf(DeliveryMethod.Deliver) }
    var instructions by remember { mutableStateOf("") }
    var cutlery by remember { mutableStateOf(false) }
    var napkins by remember { mutableStateOf(false) }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        bottomBar = {
            ChaskiFlowBottomBar(
                selected = FlowTab.Home,
                onTabClick = onTabSelected,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXl),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = "Finalizar compra",
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

            item(key = "address") {
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
                        text = "Calle Balozia 123, Miraflores",
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

            item(key = "delivery-title") {
                Text(
                    text = "Método de entrega",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                    ),
                )
            }

            item(key = "delivery-methods") {
                Column {
                    DeliveryOption(
                        label = "Recoger en el restaurante",
                        selected = method == DeliveryMethod.PickUp,
                        onClick = { method = DeliveryMethod.PickUp },
                    )
                    DeliveryOption(
                        label = "Entregar en mi puerta",
                        selected = method == DeliveryMethod.Deliver,
                        onClick = { method = DeliveryMethod.Deliver },
                    )
                }
            }

            item(key = "instructions-title") {
                Text(
                    text = "Agregar instrucciones de entrega",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF010F07),
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                        bottom = ChaskiDimens.SpacingSm,
                    ),
                )
            }

            item(key = "instructions") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding),
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        placeholder = {
                            Text(
                                text = "Por favor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF616161),
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
            }

            item(key = "extras-title") {
                Text(
                    text = "Extras",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingLg,
                    ),
                )
            }

            item(key = "extras") {
                Column {
                    ExtraOption(
                        label = "Incluir cubiertos",
                        checked = cutlery,
                        onClick = { cutlery = !cutlery },
                    )
                    ExtraOption(
                        label = "Incluir servilletas",
                        checked = napkins,
                        onClick = { napkins = !napkins },
                    )
                }
            }

            item(key = "continue") {
                ChaskiButton(
                    text = "CONTINUAR AL PAGO",
                    onClick = onContinueToPayment,
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
private fun DeliveryOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) ChaskiPrice else ChaskiTextMuted,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF757575),
        )
    }
}

@Composable
private fun ExtraOption(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (checked) ChaskiSecondary else ChaskiTextMuted,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) Color(0xFF868686) else Color(0xFF757575),
        )
    }
}