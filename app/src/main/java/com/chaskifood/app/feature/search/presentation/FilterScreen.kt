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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiFlowBottomBar
import com.chaskifood.app.ui.components.FlowTab
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private val sortOptions = listOf("Entrega rápida", "Recomendados", "Más cercanos", "Más populares")
private val dietOptions = listOf("Vegetariano", "Sin gluten", "Más populares")
private val cuisineOptions = listOf(
    "Todos",
    "Cena",
    "Hamburguesas",
    "Pizza",
    "Sopas",
    "Desayunos",
    "Americana",
    "Papas",
    "Italiana",
)
private val priceOptions = listOf("Upto $5", "Hasta $10", "Hasta $15")
private val feeOptions = listOf("Upto $5", "Hasta $10", "Hasta $15")

private val defaultSort = "Recomendados"
private val defaultDiet = "Sin gluten"
private val defaultCuisine = "Americana"
private val defaultFee = "Upto $5"

@Composable
fun FilterScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onShowResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSort by rememberSaveable { mutableStateOf(defaultSort) }
    var selectedDiet by rememberSaveable { mutableStateOf(defaultDiet) }
    var selectedCuisine by rememberSaveable { mutableStateOf(defaultCuisine) }
    var selectedPrice by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFee by rememberSaveable { mutableStateOf(defaultFee) }

    fun clearAll() {
        selectedSort = defaultSort
        selectedDiet = defaultDiet
        selectedCuisine = defaultCuisine
        selectedPrice = null
        selectedFee = defaultFee
    }

    Scaffold(
        modifier = modifier,
        containerColor = ChaskiBackground,
        bottomBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChaskiBackground)
                        .padding(
                            start = ChaskiDimens.ScreenPadding,
                            end = ChaskiDimens.ScreenPadding,
                            top = ChaskiDimens.SpacingSm,
                            bottom = ChaskiDimens.SpacingXs,
                        ),
                ) {
                    ChaskiButton(
                        text = "MOSTRAR LOS 8 RESULTADOS",
                        onClick = onShowResults,
                    )
                }
                ChaskiFlowBottomBar(
                    selected = FlowTab.Home,
                    onTabClick = { if (it == FlowTab.Home) onHome() },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = ChaskiDimens.SpacingLg),
        ) {
            item(key = "appbar") {
                ChaskiAppBar(
                    title = "Filtros",
                    onBackClick = onBack,
                    actions = {
                        ClearAllPill(onClick = ::clearAll)
                    },
                )
            }

            item(key = "sort") {
                RadioGroup(
                    options = sortOptions,
                    selected = selectedSort,
                    onSelect = { selectedSort = it },
                    background = Color.White,
                )
            }

            item(key = "diet-header") {
                SectionHeader(title = "Tipo de dieta")
            }
            item(key = "diet") {
                RadioGroup(
                    options = dietOptions,
                    selected = selectedDiet,
                    onSelect = { selectedDiet = it },
                    background = Color.White,
                )
            }

            item(key = "cuisine-header") {
                SectionHeader(title = "Cocina")
            }
            item(key = "cuisine") {
                ChipGroup(
                    options = cuisineOptions,
                    selected = selectedCuisine,
                    onSelect = { selectedCuisine = it },
                )
            }

            item(key = "price-header") {
                SectionHeader(title = "Rango de precio")
            }
            item(key = "price") {
                ChipGroup(
                    options = priceOptions,
                    selected = selectedPrice,
                    onSelect = { selectedPrice = it },
                )
            }

            item(key = "fee-header") {
                SectionHeader(title = "COSTO DE ENVÍO")
            }
            item(key = "fee") {
                ChipGroup(
                    options = feeOptions,
                    selected = selectedFee,
                    onSelect = { selectedFee = it },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.18.sp,
            color = Color(0xFF010F07),
            modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFFD9D9D9),
        )
    }
}

@Composable
private fun ClearAllPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFEEDED))
            .clickable(onClick = onClick)
            .padding(
                horizontal = ChaskiDimens.SpacingSm,
                vertical = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "limpiar todo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ChaskiTextMuted,
        )
    }
}

@Composable
private fun RadioGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    background: Color = Color.Transparent,
) {
    Column(modifier = Modifier.fillMaxWidth().background(background)) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(
                        horizontal = ChaskiDimens.ScreenPadding,
                        vertical = ChaskiDimens.SpacingMd,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXs),
            ) {
                Text(
                    text = option,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = ChaskiTextMuted,
                    modifier = Modifier.weight(1f),
                )
                RadioDot(selected = option == selected)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) ChaskiPrimary else Color(0xFFBDBDBD),
                shape = CircleShape,
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(ChaskiPrimary),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingSm,
            ),
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        options.forEach { option ->
            FilterChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color(0xFFFEEDED) else Color(0xFFFBFBFB)
    val border = if (selected) Color(0xFFF58585) else Color(0xFFE8E8E8)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(
                horizontal = ChaskiDimens.SpacingLg,
                vertical = ChaskiDimens.SpacingSm,
            ),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = Color(0xFF353535),
        )
    }
}