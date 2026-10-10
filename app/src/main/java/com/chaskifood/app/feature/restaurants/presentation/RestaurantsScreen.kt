package com.chaskifood.app.feature.restaurants.presentation

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextSubtle

private data class HotelCard(
    val name: String,
    val cuisine: String,
    val rating: String,
    val time: String,
    val fee: String,
)

private data class MasonryRestaurant(
    val name: String,
    val cuisine: String,
    val rating: String,
    val time: String,
    val fee: String,
    val imageHeight: Int,
)

private val horizontalCards = listOf(
    HotelCard("Ocean Bistro", "Casa de verano", "4.6", "21 min", "$3.0"),
    HotelCard("Garden Dining Room", "Casa de verano", "4.6", "10-20 min", "$3.0"),
    HotelCard("The Flavorful Table", "Casa de verano", "4.6", "10-20 min", "$3.0"),
    HotelCard("Starbelly", "Casa de verano", "4.6", "10-20 min", "$3.0"),
    HotelCard("Seasons", "Casa de verano", "4.6", "10-20 min", "$3.0"),
    HotelCard("North Beach Restaurant", "Casa de verano", "4.6", "10-20 min", "$3.0"),
)

private val leftColumn = listOf(
    MasonryRestaurant("Ocean Bistro", "Casa de verano", "4.6", "21 min", "$3.0", imageHeight = 280),
    MasonryRestaurant("The Flavorful Table", "Casa de verano", "4.5", "33 min", "$1.0", imageHeight = 178),
    MasonryRestaurant("North Beach Restaurant", "Casa de verano", "4.0", "4 min", "$1.0", imageHeight = 231),
    MasonryRestaurant("Chewy Balls", "Casa de verano", "3.2", "17 min", "$1.0", imageHeight = 174),
)

private val rightColumn = listOf(
    MasonryRestaurant("Garden Dining Room", "Casa de verano", "4.6", "12 min", "$8.0", imageHeight = 215),
    MasonryRestaurant("Starbelly", "Casa de verano", "4.1", "40 min", "$8.0", imageHeight = 175),
    MasonryRestaurant("The Table at Season To Taste", "Casa de verano", "4.4", "25 min", "$8.0", imageHeight = 275),
)

@Composable
fun RestaurantsScreen(
    onBack: () -> Unit,
    onTabSelected: (FlowTab) -> Unit,
    onRestaurantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = "Restaurantes",
                    onBackClick = onBack,
                )
            }

        items(horizontalCards, key = { it.name }) { card ->
            HorizontalHotelCard(
                hotel = card,
                modifier = Modifier
                    .padding(horizontal = ChaskiDimens.ScreenPadding)
                    .padding(bottom = ChaskiDimens.SpacingMd),
                onClick = { onRestaurantClick(card.name) },
            )
        }

        item(key = "masonry-title") {
            Text(
                text = "Todos los restaurantes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    start = ChaskiDimens.ScreenPadding,
                    end = ChaskiDimens.ScreenPadding,
                    top = ChaskiDimens.SpacingSm,
                    bottom = ChaskiDimens.SpacingMd,
                ),
            )
        }

        item(key = "masonry") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChaskiDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd + 6.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                ) {
                    leftColumn.forEach { r ->
                        MasonryRestaurantCard(
                            restaurant = r,
                            onClick = { onRestaurantClick(r.name) },
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                ) {
                    rightColumn.forEach { r ->
                        MasonryRestaurantCard(
                            restaurant = r,
                            onClick = { onRestaurantClick(r.name) },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HorizontalHotelCard(
    hotel: HotelCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 192.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                ImagePlusIcon(
                    modifier = Modifier.align(Alignment.Center),
                    tint = Color(0xFFBDBDBD),
                )
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ChaskiDimens.SpacingMd)
                        .size(24.dp),
                    tint = Color.White,
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x66000000))
                        .padding(horizontal = ChaskiDimens.SpacingSm, vertical = ChaskiDimens.SpacingSm),
                    verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                            Text(
                                text = hotel.time,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeliveryDining,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                            Text(
                                text = hotel.fee,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RatingChip(
                            rating = hotel.rating,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            Spacer(Modifier.width(ChaskiDimens.SpacingMd))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = ChaskiDimens.SpacingXs),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
            ) {
                Text(
                    text = hotel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hotel.cuisine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChaskiTextSubtle,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MasonryRestaurantCard(
    restaurant: MasonryRestaurant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(restaurant.imageHeight.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4F4F4F)),
        ) {
            ImagePlusIcon(
                modifier = Modifier.align(Alignment.Center),
                tint = Color(0xFF9E9E9E),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC4F4F4F))
                    .padding(horizontal = ChaskiDimens.SpacingSm, vertical = ChaskiDimens.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFEBEBEB),
                    )
                    Text(
                        text = restaurant.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFEBEBEB),
                    )
                    Spacer(Modifier.weight(1f))
                    RatingChip(rating = restaurant.rating)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeliveryDining,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFEBEBEB),
                    )
                    Text(
                        text = restaurant.fee,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFEBEBEB),
                    )
                }
            }
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        Text(
            text = restaurant.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = restaurant.cuisine,
            style = MaterialTheme.typography.bodyMedium,
            color = ChaskiTextSubtle,
            maxLines = 1,
        )
    }
}

@Composable
private fun RatingChip(
    rating: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ChaskiPrice)
            .padding(horizontal = ChaskiDimens.SpacingSm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
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

@Composable
private fun ImagePlusIcon(
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.Filled.AddPhotoAlternate,
        contentDescription = null,
        modifier = modifier.size(32.dp),
        tint = tint,
    )
}