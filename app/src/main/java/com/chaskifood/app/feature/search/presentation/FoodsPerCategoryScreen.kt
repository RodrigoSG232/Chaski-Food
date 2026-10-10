package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice

private data class FoodItem(
    val name: String,
    val description: String,
    val price: String,
)

private val africanFoods = listOf(
    FoodItem(
        name = "Arroz Jollof",
        description = "Arroz con tomate especiado, verduras y carne o pescado",
        price = "$ 15.00",
    ),
    FoodItem(
        name = "Injera con Doro Wat",
        description = "Pan plano esponjoso con guiso picante de pollo.",
        price = "$ 15.00",
    ),
    FoodItem(
        name = "Chapati",
        description = "Pan plano, suele servirse con guisos o curris.",
        price = "$ 1.50",
    ),
    FoodItem(
        name = "Pollo Piri Piri",
        description = "Pollo a la parrilla marinado en salsa picante piri piri",
        price = "$ 1.50",
    ),
)

@Composable
fun FoodsPerCategoryScreen(
    categoryName: String,
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    onFoodClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
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
            contentPadding = PaddingValues(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingXl,
                bottom = ChaskiDimens.SpacingXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = categoryName,
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
            items(africanFoods, key = { it.name }) { food ->
                FoodCard(
                    food = food,
                    onClick = { onFoodClick(food.name) },
                )
            }
        }
    }
}

@Composable
private fun FoodCard(
    food: FoodItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(119.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
            .background(Color(0xFFFBFBFB))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(119.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .background(Color(0xFFD9D9D9)),
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                tint = Color(0xFF9E9E9E),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = ChaskiDimens.SpacingLg)
                .padding(vertical = ChaskiDimens.SpacingMd),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = food.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = food.description,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp,
                color = Color(0xFF9E9E9E),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = food.price,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ChaskiPrice,
            )
        }
    }
}