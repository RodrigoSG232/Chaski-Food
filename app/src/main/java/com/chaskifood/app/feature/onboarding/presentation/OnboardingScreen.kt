@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.onboarding.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.chaskifood.app.ui.theme.ChaskiAction
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiOnPrimary
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextDisabled
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val ChaskiHeroBackground = Color(0xFFD9D9D9)

private val slides = listOf(
    OnboardingSlide(
        icon = Icons.Filled.Fastfood,
        title = "Satisfy your cravings with ease",
        subtitle = "Get fresh & healthy meals whenever and wherever",
    ),
    OnboardingSlide(
        icon = Icons.Filled.RestaurantMenu,
        title = "Discover restaurants with just a tap",
        subtitle = "Get fresh & healthy meals whenever and wherever",
    ),
    OnboardingSlide(
        icon = Icons.Filled.DeliveryDining,
        title = "Get meals delivered to your doorstep",
        subtitle = "Get fresh & healthy meals whenever and wherever",
    ),
)

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiSurface),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingSlide(slides[page])
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = ChaskiDimens.ScreenPadding,
                    end = ChaskiDimens.ScreenPadding,
                    bottom = ChaskiDimens.SpacingXxl,
                ),
        ) {
            OnboardingIndicator(
                pageCount = slides.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))

            Surface(
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding()
                        onGetStarted()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(percent = 50),
                color = ChaskiAction,
                contentColor = ChaskiOnPrimary,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = ChaskiDimens.SpacingXl)
                        .height(ChaskiDimens.BottomNavHeight - 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(ChaskiDimens.SpacingLg),
                    )
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    Text(
                        text = if (isLastPage) "GET STARTED" else "NEXT",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.width(ChaskiDimens.SpacingSm))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(ChaskiDimens.SpacingLg),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlide(slide: OnboardingSlide, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().statusBarsPadding(),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(216.dp)
                .clip(CircleShape)
                .background(ChaskiHeroBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = slide.icon,
                contentDescription = null,
                tint = ChaskiTextMuted,
                modifier = Modifier.size(104.dp),
            )
        }

        Spacer(Modifier.height(ChaskiDimens.SpacingXl + 8.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = slide.title,
                fontSize = 24.sp,
                color = ChaskiTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXl))

            Text(
                text = slide.subtitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ChaskiTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = ChaskiDimens.SpacingXl + 8.dp),
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OnboardingIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 12.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) ChaskiPrimary else ChaskiTextDisabled),
            )
        }
    }
}