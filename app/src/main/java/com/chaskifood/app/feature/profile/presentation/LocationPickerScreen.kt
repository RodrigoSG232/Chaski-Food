package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary

private data class SavedPlace(
    val name: String,
    val area: String,
)

private val savedPlaces = listOf(
    SavedPlace(name = "Balozia cerca, ABC", area = "Siro"),
    SavedPlace(name = "Local Abc", area = "Narobi (actual)"),
    SavedPlace(name = "Zona Calozi", area = "Río Ati"),
)

@Composable
fun LocationPickerScreen(
    onConfirm: (Int) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        ProfileTopBar(
            title = "Ubicación",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            savedPlaces.forEachIndexed { index, place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .clickable { selected = index }
                        .padding(horizontal = ChaskiDimens.SpacingLg, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF212121),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = place.area,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF757575),
                        )
                    }
                    RadioButton(
                        selected = index == selected,
                        onClick = { selected = index },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ChaskiPrimary,
                            unselectedColor = Color(0xFF9E9E9E),
                        ),
                    )
                }
            }

            Column(modifier = Modifier.padding(vertical = ChaskiDimens.SpacingXl)) {
                ChaskiBlackActionButton(
                    text = "AGREGAR UBICACIÓN",
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.padding(horizontal = ChaskiDimens.SpacingLg),
                )
            }
        }
    }
}