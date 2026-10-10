package com.chaskifood.app.feature.restaurants.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.components.HomeIcons
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiSecondary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextSubtle

private data class FoodItem(val name: String, val description: String, val price: String)

private val foodCards = listOf(
    FoodItem("Sándwich de galleta", "Shortbread, galletas de chocolate y red velvet.", "$ 15.00"),
    FoodItem("Combo de hamburguesa", "Shortbread, galletas de chocolate y red velvet.", "$ 11.00"),
    FoodItem("Combo sándwich", "Shortbread, galletas de chocolate y red velvet.", "$ 5.00"),
)

private val menuOptions = listOf("Pastelería", "Desayunos", "Almuerzo", "Cena")
private val categories = listOf("Entrantes", "Bebidas", "Postres", "Mariscos", "Res")

@Composable
fun RestaurantDetailScreen(
    restaurantName: String,
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    onFoodClick: (String) -> Unit,
    onOpenFilter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                    Column {
                        Text(
                            text = "Entregar en",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChaskiPrice,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                        ) {
                            Text(
                                text = restaurantName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Icon(
                                imageVector = HomeIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenFilter) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Filtros",
                        )
                    }
                }
            }

            item(key = "cover") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(360f / 264f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(56.dp),
                        tint = Color(0xFFBDBDBD),
                    )
                }
            }

            item(key = "title") {
                Column(
                    modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding, vertical = ChaskiDimens.SpacingLg),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                ) {
                    Text(
                        text = restaurantName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ChaskiPrice)
                                .padding(horizontal = ChaskiDimens.SpacingSm, vertical = ChaskiDimens.SpacingXs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                            Text(
                                text = "4.2",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                        Dot()
                        Text(
                            text = "32 min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Dot()
                        Text(
                            text = "Envío gratis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "categories-title") {
                SeeAllHeader(title = "Categorías")
            }

            item(key = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ChaskiDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                ) {
                    items(categories) { category ->
                        CategoryChip(label = category)
                    }
                }
            }

            item(key = "popular-title-1") {
                PopularTitle()
            }

            items(foodCards, key = { "$it.name-1" }) { food ->
                DetailFoodCard(
                    food = food,
                    onClick = { onFoodClick(food.name) },
                )
            }

            item(key = "menu-options-title") {
                SeeAllHeader(title = "Opciones del menú")
            }

            item(key = "menu-options") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                ) {
                    menuOptions.chunked(2).forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                        ) {
                            row.forEach { option ->
                                MenuOptionCard(
                                    label = option,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item(key = "popular-title-2") {
                PopularTitle()
            }

            items(foodCards, key = { "$it.name-2" }) { food ->
                DetailFoodCard(
                    food = food,
                    onClick = { onFoodClick(food.name) },
                )
            }

            item(key = "specials") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ChaskiDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                ) {
                    SpecialBanner(
                        label = "Sándwich de galleta",
                        modifier = Modifier.weight(1f),
                    )
                    SpecialBanner(
                        label = "Hamburguesa",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeeAllHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChaskiDimens.ScreenPadding)
            .padding(vertical = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF010F07),
        )
        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFD9D9D9)),
        )
        Text(
            text = "Ver todo",
            style = MaterialTheme.typography.bodyLarge,
            color = ChaskiPrice,
            modifier = Modifier.padding(start = ChaskiDimens.SpacingMd),
        )
    }
}

@Composable
private fun PopularTitle() {
    Text(
        text = "Más populares",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(
            start = ChaskiDimens.ScreenPadding,
            end = ChaskiDimens.ScreenPadding,
            top = ChaskiDimens.SpacingLg,
            bottom = ChaskiDimens.SpacingMd,
        ),
    )
}

@Composable
private fun CategoryChip(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(ChaskiSurface)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(36.dp),
            )
            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingSm)
            .clip(RoundedCornerShape(36.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F0)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ChaskiTextSubtle,
        )
    }
}

@Composable
private fun DetailFoodCard(
    food: FoodItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChaskiDimens.ScreenPadding)
            .padding(bottom = ChaskiDimens.SpacingMd)
            .clip(RoundedCornerShape(8.dp))
            .background(ChaskiBackground)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
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
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                tint = Color(0xFF9E9E9E),
            )
        }
        Spacer(Modifier.width(ChaskiDimens.SpacingLg))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        ) {
            Text(
                text = food.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = food.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = food.price,
                style = MaterialTheme.typography.bodyLarge,
                color = ChaskiPrice,
            )
        }
    }
}

@Composable
private fun MenuOptionCard(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(182f / 112f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(32.dp),
            tint = Color(0xFF9E9E9E),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = ChaskiTextSubtle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(ChaskiDimens.SpacingSm),
        )
    }
}

@Composable
private fun SpecialBanner(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(328f / 192f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFFBFBFB),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(Color(0xD9D9D933))
                .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingSm),
        )
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(Color(0xFFBDBDBD)),
    )
}