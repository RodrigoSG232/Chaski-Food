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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiTextMuted

private data class CategorySection(
    val letter: String,
    val categories: List<String>,
)

private val categorySections = listOf(
    CategorySection(
        letter = "A",
        categories = listOf(
            "Alcohol",
            "Africana",
            "Asiática",
            "Entrantes",
            "Antipastos",
            "Americana",
        ),
    ),
    CategorySection(
        letter = "B",
        categories = listOf(
            "Horneados",
            "Barbacoa",
            "Desayunos",
            "Hamburguesas",
            "Brunch",
            "Panes",
            "Cajas bento",
        ),
    ),
    CategorySection(
        letter = "C",
        categories = listOf(
            "Cazuelas",
            "Pastas",
            "Curris",
            "Cereales",
            "Chocolates",
            "Galletas",
            "Cócteles",
        ),
    ),
    CategorySection(
        letter = "M",
        categories = listOf(
            "Magdalenas",
            "Champiñones",
        ),
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchCategoriesScreen(
    onBack: (() -> Unit)? = null,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground),
    ) {
        if (searching) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    query = ""
                    searching = false
                },
            )
        } else {
            CategoriesHeader(
                onBack = onBack,
                onSearchClick = { searching = true },
            )
        }

        val filtered = remember(query) {
            if (query.isBlank()) {
                categorySections
            } else {
                categorySections
                    .map { section ->
                        section.copy(
                            categories = section.categories.filter {
                                it.startsWith(query, ignoreCase = true)
                            },
                        )
                    }
                    .filter { it.categories.isNotEmpty() }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ChaskiDimens.ScreenPadding,
                end = ChaskiDimens.ScreenPadding,
                top = ChaskiDimens.SpacingLg,
                bottom = ChaskiDimens.SpacingXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
        ) {
            filtered.forEach { section ->
                item(key = "header-${section.letter}") {
                    Text(
                        text = section.letter,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF6A6A6A),
                    )
                }
                item(key = "pills-${section.letter}") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
                    ) {
                        section.categories.forEach { category ->
                            CategoryPill(
                                text = category,
                                onClick = { onCategoryClick(category) },
                            )
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Sin resultados para \"$query\"",
                        fontSize = 15.sp,
                        color = ChaskiTextMuted,
                        modifier = Modifier.padding(vertical = ChaskiDimens.SpacingXl),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesHeader(
    onBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                )
            }
        } else {
            Spacer(Modifier.width(56.dp))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Categorías",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .padding(end = ChaskiDimens.SpacingMd)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEEEEEE))
                .clickable(onClick = onSearchClick)
                .padding(
                    horizontal = ChaskiDimens.SpacingMd,
                    vertical = ChaskiDimens.SpacingSm,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Buscar",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF616161),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChaskiDimens.ScreenPadding)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEEEEE))
            .padding(horizontal = ChaskiDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF616161),
        )
        Spacer(Modifier.width(ChaskiDimens.SpacingSm))
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
        if (query.isNotEmpty()) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Limpiar",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF616161),
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(32))
            .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(32))
            .clickable(onClick = onClick)
            .padding(
                horizontal = 20.dp,
                vertical = ChaskiDimens.SpacingMd,
            ),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = Color(0xFF6A6A6A),
            textAlign = TextAlign.Center,
        )
    }
}