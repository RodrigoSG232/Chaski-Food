package com.chaskifood.app.feature.orders.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chaskifood.app.ui.components.ChaskiAppBar
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiTextTertiary

/** Pestaña «Pedidos» del cliente (DEC-HU34-29): conserva la cabecera «Tus pedidos» y muestra las compras reales (HU17). */
@Composable
fun OrdersScreen(onOpenOrder: (String) -> Unit, modifier: Modifier = Modifier) {
    BuyerOrdersScreen(onOpenOrder, modifier, header = {
        Text("Tus pedidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ChaskiTextPrimary,
            modifier = Modifier.fillMaxWidth().padding(start = ChaskiDimens.SpacingLg, end = ChaskiDimens.SpacingLg,
                top = ChaskiDimens.SpacingLg, bottom = ChaskiDimens.SpacingMd))
    })
}

/** «Mis compras» para propietario y responsable (DEC-HU34-01/07): misma lista, con barra y regreso a Cuenta. */
@Composable
fun MyOrdersScreen(onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    Scaffold(containerColor = ChaskiBackground, topBar = { ChaskiAppBar(title = "Mis compras", onBackClick = onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            BuyerOrdersScreen(onOpenOrder, header = {
                Text("Tus compras personales. Los pedidos que recibe tu negocio se gestionan desde la primera pestaña.",
                    style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary,
                    modifier = Modifier.fillMaxWidth().padding(start = ChaskiDimens.SpacingLg, end = ChaskiDimens.SpacingLg,
                        bottom = ChaskiDimens.SpacingMd))
            })
        }
    }
}
