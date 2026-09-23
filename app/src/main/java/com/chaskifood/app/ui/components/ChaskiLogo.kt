package com.chaskifood.app.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiPrimary

val ChaskiLogo: ImageVector = ImageVector.Builder(
    defaultWidth = 40.dp,
    defaultHeight = 40.dp,
    viewportWidth = 40f,
    viewportHeight = 40f,
).addPath(
    name = "ChaskiLogo",
    pathData = addPathNodes(
        "M28 0l-16 0c-6.6 0-12 5.4-12 12l0 16c0 6.6 5.4 12 12 12l28 0 0-28c0-6.6-5.4-12-12-12z " +
            "m0 28l-10 0c-3.3 0-6-2.7-6-6l0-4c0-3.3 2.7-6 6-6l4 0c3.3 0 6 2.7 6 6l0 10z",
    ),
    fill = SolidColor(ChaskiPrimary),
).build()