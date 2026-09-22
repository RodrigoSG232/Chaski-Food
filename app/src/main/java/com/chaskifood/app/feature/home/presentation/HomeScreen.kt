@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.discover.domain.Restaurant
import com.chaskifood.app.ui.components.ErrorState
import com.chaskifood.app.ui.components.LoadingState
import com.chaskifood.app.ui.components.collectChaskiState
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiSecondary

private val HomeOfferBg = Color(0xFF212121)
private val HomeOfferFg = Color(0xFFFBFBFB)
private val HomeStarGold = Color(0xFFFFCB11)
private val HomeOnBadge = Color(0xFFF6F6F6)
private val HomeImgGray = Color(0xFFD9D9D9)
private val HomeBigImgBg = Color(0xFFEEEEEE)
private val HomeDot = Color(0xFF909090)
private val HomeSubtle = Color(0xFF9E9E9E)
private val HomeMeta = Color(0xFF616161)
private val HomeMetaDark = Color(0xFF505050)
private val HomeTag = Color(0xFF868686)
private val HomeHeading = Color(0xFF010F07)

private data class HomeCategory(val label: String, val icon: ImageVector)

private val categories = listOf(
    HomeCategory("Hamburguesas", Icons.Filled.Fastfood),
    HomeCategory("Pizza", Icons.Filled.LocalPizza),
    HomeCategory("Sushi", Icons.Filled.RamenDining),
    HomeCategory("Pollo a la brasa", Icons.Filled.KebabDining),
    HomeCategory("Café", Icons.Filled.LocalCafe),
    HomeCategory("Postres", Icons.Filled.Cake),
    HomeCategory("Bebidas", Icons.Filled.EmojiFoodBeverage),
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by collectChaskiState(viewModel.uiState)
    val uiState = state

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiSurface),
        contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXl),
    ) {
        item(key = "appbar") { HomeAppBar() }

        item(key = "offer") {
            OfferCard(
                modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
            )
        }

        when (uiState) {
            is UiState.Loading -> item(key = "loading") {
                LoadingState(modifier = Modifier.padding(top = 48.dp))
            }

            is UiState.Error -> item(key = "error") {
                ErrorState(
                    message = uiState.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }

            is UiState.Success -> {
                val restaurants = uiState.data

                item(key = "featured-title") {
                    SectionTitle(
                        title = "Restaurantes destacados",
                        seeAllColor = ChaskiTextMuted,
                    )
                }
                item(key = "featured") {
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = ChaskiDimens.ScreenPadding,
                            end = ChaskiDimens.ScreenPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.ScreenPadding),
                    ) {
                        items(restaurants, key = { it.id }) { restaurant ->
                            FeaturedRestaurantCard(restaurant = restaurant)
                        }
                    }
                }

                item(key = "categories-title") {
                    SectionTitle(
                        title = "Categorías",
                        seeAllColor = ChaskiPrimary,
                    )
                }
                item(key = "categories") {
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = ChaskiDimens.ScreenPadding,
                            end = ChaskiDimens.ScreenPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.ScreenPadding),
                    ) {
                        items(categories) { category ->
                            FoodCategoryCard(category = category)
                        }
                    }
                }

                item(key = "popular-title") {
                    SectionTitle(
                        title = "Populares",
                        seeAllColor = ChaskiPrimary,
                    )
                }
                items(restaurants, key = { "popular-${it.id}" }) { restaurant ->
                    BigRestaurantCard(
                        restaurant = restaurant,
                        modifier = Modifier.padding(
                            start = ChaskiDimens.ScreenPadding,
                            end = ChaskiDimens.ScreenPadding,
                            bottom = ChaskiDimens.SpacingLg,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAppBar(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingLg,
                bottom = ChaskiDimens.SpacingLg,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = ChaskiTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "San Isidro, Lima",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = ChaskiTextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ShoppingBasket,
                contentDescription = "Carrito",
                tint = ChaskiTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ChaskiSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "5",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5FFF9),
                )
            }
        }
    }
}

@Composable
private fun OfferCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = HomeOfferBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .padding(ChaskiDimens.SpacingXl),
        ) {
            Text(
                text = "40% OFF",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HomeOfferFg,
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingMd))
            Text(
                text = "Por pedir más de 4 veces el último mes",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingLg))
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = HomeOfferFg,
            ) {
                Text(
                    text = "Claim now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeOfferBg,
                    modifier = Modifier.padding(
                        horizontal = ChaskiDimens.SpacingLg,
                        vertical = ChaskiDimens.SpacingSm,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, seeAllColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingXxl,
                bottom = ChaskiDimens.SpacingSm,
            ),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = HomeHeading,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Ver todo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = seeAllColor,
        )
    }
}

@Composable
private fun FeaturedRestaurantCard(
    restaurant: Restaurant,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(225.dp)) {
        HomeImage(
            bg = HomeImgGray,
            height = 143.dp,
            iconSize = 40.dp,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        Text(
            text = restaurant.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChaskiTextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = restaurant.cuisine,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HomeSubtle,
            maxLines = 1,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        MetaRow(
            rating = restaurant.rating,
            timeMin = restaurant.deliveryTimeMin,
            feeText = feeText(restaurant.deliveryFee),
        )
    }
}

@Composable
private fun FoodCategoryCard(
    category: HomeCategory,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(96.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(HomeImgGray),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = ChaskiTextMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        Text(
            text = category.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = HomeTag,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BigRestaurantCard(
    restaurant: Restaurant,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeImage(
            bg = HomeBigImgBg,
            height = 184.dp,
            iconSize = 48.dp,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        Text(
            text = restaurant.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeHeading,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cuisines = restaurant.cuisine.split("·").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            cuisines.forEachIndexed { index, cuisine ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(HomeDot),
                    )
                }
                Text(
                    text = cuisine,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeTag,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        MetaRow(
            rating = restaurant.rating,
            timeMin = restaurant.deliveryTimeMin,
            feeText = feeText(restaurant.deliveryFee),
        )
    }
}

@Composable
private fun MetaRow(rating: Double, timeMin: Int, feeText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HomeRatingBadge(rating = rating)
        Dot()
        MetaItem(icon = Icons.Filled.AccessTime, text = "$timeMin min")
        Dot()
        MetaItem(icon = Icons.Filled.DeliveryDining, text = feeText)
    }
}

@Composable
private fun HomeRatingBadge(rating: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChaskiPrimary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = HomeStarGold,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = rating.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HomeOnBadge,
        )
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(4.dp)
            .clip(CircleShape)
            .background(HomeDot),
    )
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HomeMetaDark,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HomeMeta,
        )
    }
}

@Composable
private fun HomeImage(bg: Color, height: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            tint = ChaskiTextMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun feeText(fee: Double): String =
    if (fee <= 0.0) "Envío gratis" else "S/ ${fee.toInt()}"