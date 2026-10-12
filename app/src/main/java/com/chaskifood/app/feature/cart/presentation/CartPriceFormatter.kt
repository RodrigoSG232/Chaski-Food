package com.chaskifood.app.feature.cart.presentation

import java.math.BigDecimal

/** Presenta céntimos de PEN sin pasar por Float o Double. */
internal fun formatCartPrice(minor: Long): String =
    "S/ ${BigDecimal.valueOf(minor, 2).toPlainString()}"
