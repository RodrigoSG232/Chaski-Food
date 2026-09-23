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
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.R
import com.chaskifood.app.ui.components.HomeIcons
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiOnSecondary
import com.chaskifood.app.ui.theme.ChaskiSecondary
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextTertiary
import com.chaskifood.app.ui.theme.ChaskiTextSubtle
import com.chaskifood.app.ui.theme.ChaskiTextDisabled
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiPrimary

private val HomeOfferBg = Color(0xFF212121)
private val HomeOfferFg = Color(0xFFFBFBFB)
private val HomeHeading = Color(0xFF010F07)
private val HomeStarGold = Color(0xFFFFCB11)
private val HomeOnBadge = Color(0xFFF6F6F6)
private val HomeMetaDark = Color(0xFF505050)
private val HomeDotFeatured = Color(0xFF9E9E9E)
private val HomeDotBig = Color(0xFF909090)
private val HomeImgGray = Color(0xFFD9D9D9)
private val HomeBigImgBg = Color(0xFFEEEEEE)

private data class FeaturedCard(
    val name: String,
    val subtitle: String,
    val rating: String,
    val time: String,
    val fee: String,
    val imageRes: Int?,
)

private data class CategoryItem(val label: String, val imageRes: Int)

private data class PopularCard(
    val name: String,
    val cuisines: List<String>,
    val rating: String,
    val time: String,
    val fee: String,
)

private val featuredCards = listOf(
    FeaturedCard("67 Athi hotel", "Summer house, Athi river", "4.2", "32 min", "Envío gratis", R.drawable.home_featured),
    FeaturedCard("The white resort", "Sumbulah road", "4.0", "20 min", "$3", R.drawable.home_featured),
    FeaturedCard("The white resort", "Sumbulah road", "4.0", "20 min", "$3", null),
)

private val categories = listOf(
    CategoryItem("Hamburguesas", R.drawable.home_category),
    CategoryItem("Pizza", R.drawable.home_category),
    CategoryItem("Ensaladas", R.drawable.home_category),
    CategoryItem("Sushi", R.drawable.home_category),
    CategoryItem("Postres", R.drawable.home_category),
)

private val popularCards = listOf(
    PopularCard("Silver Inn", listOf("Comida tailandesa"), "4.2", "32 min", "Gratis"),
    PopularCard("Hillside Retreat", listOf("Comida tailandesa", "Americana", "Italiana"), "4.2", "22 min", "$5"),
    PopularCard("Luxe Residences", listOf("Comida francesa", "Vegetariana", "India"), "4.2", "22 min", "$5"),
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground),
        contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingXxl),
    ) {
        item(key = "appbar") { HomeAppBar() }

        item(key = "offer") {
            OfferCard(
                modifier = Modifier
                    .padding(horizontal = ChaskiDimens.ScreenPadding)
                    .padding(top = ChaskiDimens.SpacingLg),
            )
        }

        item(key = "featured-title") {
            SectionTitle(title = "Restaurantes destacados")
        }
        item(key = "featured") {
            LazyRow(
                contentPadding = PaddingValues(
                    start = ChaskiDimens.ScreenPadding,
                    end = ChaskiDimens.ScreenPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXl),
            ) {
                items(featuredCards, key = { it.name + it.time }) { card ->
                    FeaturedRestaurantCard(card = card)
                }
            }
        }

        item(key = "categories-title") {
            SectionTitle(title = "Categorías")
        }
        item(key = "categories") {
            LazyRow(
                contentPadding = PaddingValues(
                    start = ChaskiDimens.ScreenPadding,
                    end = ChaskiDimens.ScreenPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
            ) {
                items(categories, key = { it.label + it.imageRes }) { category ->
                    FoodCategoryCard(category = category)
                }
            }
        }

        item(key = "popular-title") {
            SectionTitle(title = "Populares")
        }
        items(popularCards, key = { it.name }) { card ->
            BigRestaurantCard(
                card = card,
                modifier = Modifier.padding(
                    start = ChaskiDimens.ScreenPadding,
                    end = ChaskiDimens.ScreenPadding,
                    bottom = ChaskiDimens.SpacingXl,
                ),
            )
        }
    }
}

@Composable
private fun HomeAppBar(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(72.dp)
            .padding(horizontal = ChaskiDimens.ScreenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = HomeIcons.MapPin,
                contentDescription = null,
                tint = ChaskiTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingSm))
            Text(
                text = "Athi river",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = ChaskiTextPrimary,
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingSm))
            Icon(
                imageVector = HomeIcons.ChevronDown,
                contentDescription = null,
                tint = ChaskiTextPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = HomeIcons.ShoppingBasket,
                contentDescription = "Carrito",
                tint = Color(0xFF757575),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingXs))
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
                    letterSpacing = 1.sp,
                    color = ChaskiOnSecondary,
                )
            }
        }
    }
}

@Composable
private fun OfferCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HomeOfferBg)
            .padding(start = 8.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
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
            letterSpacing = 0.28.sp,
            color = Color.White,
            lineHeight = 21.sp,
            modifier = Modifier.width(202.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(HomeOfferFg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Reclamar",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HomeOfferBg,
            )
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingXl))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingXl,
                bottom = ChaskiDimens.SpacingSm,
            ),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.18.sp,
            color = HomeHeading,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Ver todo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.25.sp,
            color = ChaskiTextMuted.copy(alpha = 0.84f),
        )
    }
}

@Composable
private fun FeaturedRestaurantCard(
    card: FeaturedCard,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(225.dp)) {
        HomeImage(
            imageRes = card.imageRes,
            bg = HomeImgGray,
            height = 143.dp,
            cornerRadius = 8.dp,
            iconSize = 32.dp,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        Text(
            text = card.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.18.sp,
            color = ChaskiTextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        Text(
            text = card.subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.18.sp,
            color = ChaskiTextDisabled,
            maxLines = 1,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        FeaturedMetaRow(
            rating = card.rating,
            time = card.time,
            fee = card.fee,
        )
    }
}

@Composable
private fun FoodCategoryCard(
    category: CategoryItem,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(96.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HomeImgGray),
        ) {
            Icon(
                painter = painterResource(id = category.imageRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        Text(
            text = category.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.18.sp,
            color = ChaskiTextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BigRestaurantCard(
    card: PopularCard,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeImage(
            imageRes = null,
            bg = HomeBigImgBg,
            height = 184.dp,
            cornerRadius = 8.dp,
            iconSize = 48.dp,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        Text(
            text = card.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.18.sp,
            color = HomeHeading,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        Row(verticalAlignment = Alignment.CenterVertically) {
            card.cuisines.forEachIndexed { index, cuisine ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(HomeDotBig),
                    )
                }
                Text(
                    text = cuisine,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.18.sp,
                    color = ChaskiTextSubtle,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
        BigMetaRow(
            rating = card.rating,
            time = card.time,
            fee = card.fee,
        )
    }
}

@Composable
private fun FeaturedMetaRow(rating: String, time: String, fee: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HomeRatingBadge(rating = rating)
        Dot(color = HomeDotFeatured)
        MetaText(text = time)
        Dot(color = HomeDotFeatured)
        MetaText(text = fee)
    }
}

@Composable
private fun BigMetaRow(rating: String, time: String, fee: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HomeRatingBadge(rating = rating)
        Dot(color = HomeDotBig)
        TimeBadge(time = time)
        Dot(color = HomeDotBig)
        FeeBadge(fee = fee)
    }
}

@Composable
private fun HomeRatingBadge(rating: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ChaskiPrimary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = HomeStarGold,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = rating,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HomeOnBadge,
        )
    }
}

@Composable
private fun TimeBadge(time: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            tint = HomeMetaDark,
            modifier = Modifier.size(16.dp),
        )
        MetaText(text = time, color = HomeMetaDark)
    }
}

@Composable
private fun FeeBadge(fee: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DeliveryDining,
            contentDescription = null,
            tint = HomeMetaDark,
            modifier = Modifier.size(16.dp),
        )
        MetaText(text = fee, color = HomeMetaDark)
    }
}

@Composable
private fun MetaText(text: String, color: Color = ChaskiTextTertiary) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.18.sp,
        color = color,
    )
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(4.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun HomeImage(
    imageRes: Int?,
    bg: Color,
    height: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (imageRes != null) {
            Icon(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = ChaskiTextDisabled,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}