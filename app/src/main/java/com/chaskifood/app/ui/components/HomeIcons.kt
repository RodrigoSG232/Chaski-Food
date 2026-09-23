package com.chaskifood.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object HomeIcons {

    val MapPin: ImageVector by lazy {
        val stroke = SolidColor(Color(0xFF212121))
        ImageVector.Builder(
            name = "MapPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 10f)
                curveTo(20f, 16f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 4f, 16f, 4f, 10f)
                curveTo(4f, 7.878f, 4.8429f, 5.8434f, 6.3431f, 4.3431f)
                curveTo(7.8434f, 2.8429f, 9.8783f, 2f, 12f, 2f)
                curveTo(14.1217f, 2f, 16.1566f, 2.8429f, 17.6569f, 4.3431f)
                curveTo(19.1571f, 5.8434f, 20f, 7.8783f, 20f, 10f)
                close()
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15f, 10f)
                arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
                arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        val stroke = SolidColor(Color(0xFF424242))
        ImageVector.Builder(
            name = "ChevronDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9f, 10f)
                lineTo(12f, 13f)
                lineTo(15f, 10f)
            }
        }.build()
    }

    val ShoppingBasket: ImageVector by lazy {
        val stroke = SolidColor(Color(0xFF757575))
        ImageVector.Builder(
            name = "ShoppingBasket",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 4f)
                lineTo(6f, 11f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14f, 4f)
                lineTo(18f, 11f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 11f)
                horizontalLineTo(20f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 11f)
                lineToRelative(1.6f, 7.4f)
                curveToRelative(0.094f, 0.459f, 0.345f, 0.87f, 0.71f, 1.163f)
                curveToRelative(0.366f, 0.292f, 0.822f, 0.447f, 1.29f, 0.437f)
                horizontalLineToRelative(9.8f)
                curveToRelative(0.9f, 0f, 1.8f, -0.7f, 2f, -1.6f)
                lineToRelative(1.7f, -7.4f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 11f)
                verticalLineTo(20f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 16f)
                horizontalLineTo(19.5f)
            }
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14f, 11f)
                verticalLineTo(20f)
            }
        }.build()
    }
}