@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.onboarding.presentation

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.R
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiOnPrimary
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiTextDisabled
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val imageRes: Int,
    val title: String,
    val subtitle: String,
)

private val ChaskiHeroBackground = Color(0xFFD9D9D9)

private val slides = listOf(
    OnboardingSlide(
        imageRes = R.drawable.onboarding_1,
        title = "Satisface tus antojos sin esfuerzo",
        subtitle = "Comidas frescas y saludables dondequiera que estés",
    ),
    OnboardingSlide(
        imageRes = R.drawable.onboarding_2,
        title = "Descubre restaurantes con un solo toque",
        subtitle = "Comidas frescas y saludables dondequiera que estés",
    ),
    OnboardingSlide(
        imageRes = R.drawable.onboarding_3,
        title = "Recibe tus comidas en tu puerta",
        subtitle = "Comidas frescas y saludables dondequiera que estés",
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
                    bottom = 40.dp,
                ),
        ) {
            OnboardingIndicator(
                pageCount = slides.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(64.dp))

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
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF212121),
                contentColor = ChaskiOnPrimary,
            ) {
                if (isLastPage) {
                    Text(
                        text = "EMPEZAR",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(
                            horizontal = 48.dp,
                            vertical = 16.dp,
                        ),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(
                            horizontal = 48.dp,
                            vertical = 16.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Continuar",
                            modifier = Modifier.size(20.dp),
                            tint = ChaskiOnPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlide(slide: OnboardingSlide, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(130.dp))

        Box(
            modifier = Modifier
                .size(216.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(slide.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(40.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = slide.title,
                fontSize = 24.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.15.sp,
                color = ChaskiTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(272.dp),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = slide.subtitle,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.64.sp,
                fontWeight = FontWeight.Medium,
                color = ChaskiTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(266.dp),
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) ChaskiPrimary else ChaskiTextDisabled),
            )
        }
    }
}