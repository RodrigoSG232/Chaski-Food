package com.chaskifood.app.feature.checkout.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RealOrderConfirmedScreen(orderId: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Pedido enviado", style = MaterialTheme.typography.headlineMedium)
        Text("Estado: pendiente de confirmación del local")
        Text("Código: $orderId", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text("VOLVER AL INICIO") }
    }
}
