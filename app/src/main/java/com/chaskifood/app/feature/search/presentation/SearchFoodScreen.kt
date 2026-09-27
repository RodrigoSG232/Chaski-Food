package com.chaskifood.app.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private val recentSearches = listOf(
    "Papas con chile y queso",
    "Injera con Doro Wat",
    "Bunny Chow",
)

private data class RestaurantResult(
    val name: String,
    val location: String,
    val time: String,
    val fee: String,
    val rating: String,
)

private val restaurantResults = listOf(
    RestaurantResult("Papas doradas", "Ubicación X", "10-20 min", "$3.0", "4.6"),
    RestaurantResult("Silver inn", "Ubicación X", "10-20 min", "$5.0", "4.2"),
    RestaurantResult("Silver bistro", "Ubicación Y", "5-15 min", "$3.0", "4.5"),
)

private data class FoodResult(val name: String, val restaurant: String)

private val foodResults = listOf(
    FoodResult("Hamburguesa y papas", "Ocean bistro"),
    FoodResult("Tiraditas de pollo y papas", "Ocean bistro"),
    FoodResult("Bistec y papas", "Ocean bistro"),
)

private val initialFilterChips = listOf(
    "Americana",
    "Sin gluten",
    "Costo de envío: $5",
    "Precio: $15",
)

@Composable
fun SearchFoodScreen(
    onOpenCategories: () -> Unit,
    onRestaurantClick: (String) -> Unit,
    onFoodClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var recents by remember { mutableStateOf(recentSearches) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiSurface),
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
        )

        if (query.isBlank()) {
            RecentLanding(
                recents = recents,
                onClearAll = { recents = emptyList() },
                onRemove = { term -> recents = recents - term },
                onQuery = { query = it },
                onOpenCategories = onOpenCategories,
            )
        } else {
            ResultsList(
                query = query,
                onRestaurantClick = onRestaurantClick,
                onFoodClick = onFoodClick,
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = ChaskiDimens.SpacingMd)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEEEEE))
            .padding(horizontal = ChaskiDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF616161),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFF616161)),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF616161),
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Limpiar",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF616161),
            )
        }
    }
}

@Composable
private fun RecentLanding(
    recents: List<String>,
    onClearAll: () -> Unit,
    onRemove: (String) -> Unit,
    onQuery: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChaskiDimens.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RECIENTES",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFF000000),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Limpiar todo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFFF04949),
                modifier = Modifier.clickable(onClick = onClearAll),
            )
        }

        recents.forEach { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQuery(term) }
                    .padding(vertical = ChaskiDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF7C7C7C),
                )
                Text(
                    text = term,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF7C7C7C),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(term) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF9E9E9E),
                    )
                }
            }
        }

        Spacer(Modifier.height(ChaskiDimens.SpacingMd))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32))
                .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(32))
                .clickable(onClick = onOpenCategories)
                .padding(
                    horizontal = 20.dp,
                    vertical = ChaskiDimens.SpacingSm,
                ),
        ) {
            Text(
                text = "Explorar categorías",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFF6A6A6A),
            )
        }
    }
}

@Composable
private fun ResultsList(
    query: String,
    onRestaurantClick: (String) -> Unit,
    onFoodClick: (String) -> Unit,
) {
    var chips by remember { mutableStateOf(initialFilterChips) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ChaskiDimens.SpacingLg,
            end = ChaskiDimens.SpacingLg,
            top = ChaskiDimens.SpacingSm,
            bottom = ChaskiDimens.SpacingXxl,
        ),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
    ) {
        item(key = "subtitle") {
            Text(
                text = "Mostrando 8 resultados para \u201c$query\u201d",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFF7C7C7C),
            )
        }
        item(key = "sort") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ordenar por: ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF424242),
                )
                Text(
                    text = "Restaurantes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF9E9E9E),
                )
                Spacer(Modifier.width(ChaskiDimens.SpacingXs))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF9E9E9E),
                )
            }
        }
        item(key = "chips") {
            FilterChipsRow(
                chips = chips,
                onRemove = { chip -> chips = chips - chip },
            )
        }
        items(restaurantResults, key = { "r-${it.name}" }) { restaurant ->
            RestaurantResultCard(
                restaurant = restaurant,
                onClick = { onRestaurantClick(restaurant.name) },
            )
        }
        items(foodResults, key = { "f-${it.name}" }) { food ->
            FoodResultCard(
                food = food,
                onClick = { onFoodClick(food.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    chips: List<String>,
    onRemove: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
                    .background(Color(0xFFFBFBFB))
                    .clickable { onRemove(chip) }
                    .padding(
                        start = ChaskiDimens.SpacingLg,
                        end = ChaskiDimens.SpacingSm,
                        top = ChaskiDimens.SpacingSm,
                        bottom = ChaskiDimens.SpacingSm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
            ) {
                Text(
                    text = chip,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF353535),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ChaskiTextMuted,
                )
            }
        }
    }
}

@Composable
private fun RestaurantResultCard(
    restaurant: RestaurantResult,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 156.dp, height = 192.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0)),
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = Color(0xFF9E9E9E),
            )
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ChaskiDimens.SpacingSm)
                    .size(24.dp),
                tint = Color.White,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(
                        horizontal = ChaskiDimens.SpacingSm,
                        vertical = ChaskiDimens.SpacingSm,
                    ),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
            ) {
                SearchMetaRow(
                    time = restaurant.time,
                    fee = restaurant.fee,
                    rating = restaurant.rating,
                )
            }
        }
        Spacer(Modifier.width(ChaskiDimens.SpacingMd))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = ChaskiDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
        ) {
            Text(
                text = restaurant.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFF000000),
                maxLines = 1,
            )
            Text(
                text = restaurant.location,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FoodResultCard(
    food: FoodResult,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 156.dp, height = 192.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0)),
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = Color(0xFF9E9E9E),
            )
        }
        Spacer(Modifier.width(ChaskiDimens.SpacingMd))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = ChaskiDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            Text(
                text = food.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = Color(0xFF000000),
                maxLines = 1,
            )
            Text(
                text = food.restaurant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SearchMetaRow(time: String, fee: String, rating: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = fee,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Row(
            modifier = Modifier
                .padding(start = ChaskiDimens.SpacingSm)
                .clip(RoundedCornerShape(6.dp))
                .background(ChaskiPrice)
                .padding(horizontal = ChaskiDimens.SpacingXs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
            Text(
                text = rating,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}