package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import com.chaskifood.app.ui.components.DemoNotice
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

data class OrderSummary(
    val id: String,
    val restaurant: String,
    val date: String,
    val arrival: String,
    val total: String,
    val items: List<String>,
    val status: String = "Preparando",
)

val mockOrders = listOf(
    OrderSummary(
        id = "001",
        restaurant = "Ocean bistro",
        date = "El 2/3/24",
        arrival = "Llegada est.: 2:45 p. m.",
        total = "$30",
        items = listOf("Carne molida con puré de papas", "Chapati con res"),
    ),
    OrderSummary(
        id = "002",
        restaurant = "Silver bistro",
        date = "El 2/1/24",
        arrival = "Llegada est.: 1:20 p. m.",
        total = "$18",
        items = listOf("Bistec y papas", "Ensalada César con pollo"),
        status = "Entregado",
    ),
    OrderSummary(
        id = "003",
        restaurant = "Ocean bistro",
        date = "El 1/28/24",
        arrival = "Llegada est.: 12:30 p. m.",
        total = "$24",
        items = listOf("Hamburguesa y papas", "Carne molida con puré de papas"),
    ),
)

@Composable
fun OrdersScreen(
    onTrack: (String) -> Unit,
    onDelivered: (String) -> Unit,
    modifier: Modifier = Modifier,
    orders: List<OrderSummary> = mockOrders,
    onMoreClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiSurface),
    ) {
        OrdersHeader(onMoreClick = onMoreClick)
        DemoNotice()

        if (orders.isEmpty()) {
            EmptyOrdersState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(orders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onClick = {
                            if (order.status == "Entregado") {
                                onDelivered(order.id)
                            } else {
                                onTrack(order.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdersHeader(onMoreClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = ChaskiDimens.SpacingLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tus pedidos",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = Color(0xFF212121),
        )
        Spacer(Modifier.weight(1f))
        if (onMoreClick != null) {
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Más",
                tint = Color(0xFF212121),
            )
        }
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ChaskiDimens.SpacingLg)
            .padding(top = ChaskiDimens.SpacingLg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pedido #${order.id}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.16.sp,
                color = Color(0xFF212121),
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0))
                    .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
            ) {
                Icon(
                    imageVector = if (order.status == "Entregado") {
                        Icons.Filled.Check
                    } else {
                        Icons.Filled.SoupKitchen
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF424242),
                )
                Text(
                    text = order.status,
                    fontSize = 14.sp,
                    color = Color(0xFF424242),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ChaskiDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        ) {
            Text(
                text = order.restaurant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ChaskiTextMuted,
            )
            MetaDot()
            Text(
                text = order.date,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ChaskiTextMuted,
            )
            MetaDot()
            Text(
                text = order.arrival,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ChaskiTextMuted,
            )
            MetaDot()
            Text(
                text = order.total,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = ChaskiTextMuted,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            order.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ChaskiDimens.SpacingMd),
                ) {
                    Text(
                        text = item,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.15.sp,
                        color = Color(0xFF212121),
                    )
                }
                if (index < order.items.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color(0xFFEEEEEE),
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = ChaskiDimens.SpacingLg),
            thickness = 2.dp,
            color = Color(0xFFE0E0E0),
        )
    }
}

@Composable
private fun MetaDot() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ChaskiTextMuted)
            .size(4.dp),
    )
}

@Composable
private fun EmptyOrdersState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Archive,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color(0xFF757575),
        )
        Spacer(Modifier.size(ChaskiDimens.SpacingXl))
        Text(
            text = "Tus pedidos están vacíos",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = Color(0xFF212121),
        )
        Spacer(Modifier.size(ChaskiDimens.SpacingSm))
        Text(
            text = "Todavía no tienes pedidos. Aquí aparecerán cuando hagas tu primero.",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = ChaskiDimens.SpacingLg)
                .width(272.dp),
        )
        Spacer(Modifier.weight(1f))
    }
}