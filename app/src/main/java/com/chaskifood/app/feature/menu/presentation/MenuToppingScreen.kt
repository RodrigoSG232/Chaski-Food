package com.chaskifood.app.feature.menu.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiSecondary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private val toppings = listOf(
    "Chip de chocolate",
    "Galletas y crema",
    "Mantequilla de maní",
    "Funfetti",
    "Red velvet",
    "Snicker doodle",
    "Chocolate blanco",
    "Macadamia",
)

@Composable
fun MenuToppingScreen(
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    onAddToOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTopping by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    var instructions by remember { mutableStateOf("") }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = ChaskiSurface,
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
                    title = "",
                    onBackClick = onBack,
                )
            }

            item(key = "cover") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(360f / 264f)
                        .background(Color(0xFFD9D9D9)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(56.dp),
                        tint = Color(0xFF9E9E9E),
                    )
                }
            }

            item(key = "info") {
                Column(
                    modifier = Modifier.padding(
                        start = ChaskiDimens.ScreenPadding,
                        end = ChaskiDimens.ScreenPadding,
                        top = ChaskiDimens.SpacingXl,
                        bottom = ChaskiDimens.SpacingLg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                ) {
                    Text(
                        text = "Sándwich de galleta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Shortbread, galletas de chocolate y red velvet. 8 oz de queso crema ablandado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9E9E),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFCFC6))
                            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingXs),
                    ) {
                        Text(
                            text = "Cocina americana",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChaskiPrice,
                        )
                    }
                }
            }

            item(key = "topping-title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .padding(bottom = ChaskiDimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                ) {
                    Text(
                        text = "Elige tu galleta favorita",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF010F07),
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFB4E1C3))
                            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingXs),
                    ) {
                        Text(
                            text = "Obligatorio",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChaskiSecondary,
                        )
                    }
                }
            }

            items(toppings, key = { it }) { topping ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTopping = if (selectedTopping == topping) null else topping }
                        .padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                ) {
                    Icon(
                        imageVector = if (selectedTopping == topping) {
                            Icons.Filled.RadioButtonChecked
                        } else {
                            Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (selectedTopping == topping) ChaskiSecondary else ChaskiTextMuted,
                    )
                    Text(
                        text = topping,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (topping == "Mantequilla de maní" || topping == "Chocolate blanco") {
                            Color(0xFF868686)
                        } else {
                            Color(0xFF757575)
                        },
                    )
                }
            }

            item(key = "instructions-title") {
                Text(
                    text = "Agregar instrucciones especiales",
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
                                text = "Escribe aquí...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF939393),
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

            item(key = "quantity") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ChaskiDimens.SpacingLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(ChaskiDimens.ScreenPadding))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityCircle(
                            icon = Icons.Filled.Remove,
                            onClick = { if (quantity > 1) quantity-- },
                        )
                        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
                        Text(
                            text = quantity.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF020202),
                        )
                        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
                        QuantityCircle(
                            icon = Icons.Filled.Add,
                            onClick = { quantity++ },
                        )
                    }
                    Spacer(Modifier.width(ChaskiDimens.ScreenPadding))
                }
            }

            item(key = "buy") {
                ChaskiButton(
                    text = "AGREGAR AL PEDIDO $${45 * quantity}.00",
                    onClick = onAddToOrder,
                    variant = com.chaskifood.app.ui.components.ChaskiButtonVariant.Primary,
                    modifier = Modifier
                        .padding(horizontal = ChaskiDimens.ScreenPadding)
                        .padding(bottom = ChaskiDimens.SpacingLg),
                )
            }
        }
    }
}

@Composable
private fun QuantityCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF020202),
        )
    }
}