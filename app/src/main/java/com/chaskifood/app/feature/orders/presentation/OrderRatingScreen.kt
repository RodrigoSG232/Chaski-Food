package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiSurface

@Composable
fun OrderRatingScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color(0xFF0D0D0D),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ChaskiDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingXl),
        ) {
            RatingSection(
                title = "Califica al repartidor",
                subtitle = "¿Cómo fue tu experiencia con Omosh?",
            )
            CommentField()
            RatingSection(
                title = "¿Cómo estuvo la comida?",
                subtitle = "Tus comentarios nos ayudan a mejorar los próximos pedidos.",
            )
            CommentField()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF212121))
                    .clickable(onClick = onNext)
                    .padding(vertical = ChaskiDimens.SpacingLg, horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "SIGUIENTE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.84.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(ChaskiDimens.SpacingXl))
        }
    }
}

@Composable
private fun RatingSection(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = Color(0xFF212121),
        )
        Text(
            text = subtitle,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 279.dp),
        )
        StarRatingRow()
    }
}

@Composable
private fun StarRatingRow() {
    var rating by remember { mutableStateOf(0) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        (1..5).forEach { index ->
            Icon(
                imageVector = if (index <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Estrellas $index de 5",
                tint = Color(0xFFFFA500),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { rating = index },
            )
        }
    }
}

@Composable
private fun CommentField() {
    var comment by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE0E0E0))
            .padding(horizontal = ChaskiDimens.SpacingLg, vertical = 14.dp),
    ) {
        if (comment.isEmpty()) {
            Text(
                text = "Comentario adicional...",
                fontSize = 16.sp,
                color = Color(0xFF616161),
            )
        }
        BasicTextField(
            value = comment,
            onValueChange = { comment = it },
            singleLine = false,
            cursorBrush = SolidColor(Color(0xFF616161)),
            textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF212121)),
            modifier = Modifier.fillMaxSize(),
            maxLines = 4,
        )
    }
}