package com.chaskifood.app.feature.cart.presentation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.ui.components.ChaskiButton
import com.chaskifood.app.ui.components.ChaskiButtonVariant
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrice

/** Una sola instancia visible, colocada por Navigation encima del destino activo. */
@Composable
fun CartConfirmationHost(
    state: CartUiState,
    onConfirm: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    state.confirmation?.let { confirmation ->
        CartStoreChangeDialog(
            confirmation = confirmation,
            busy = state.isBusy,
            message = state.message?.text,
            onConfirm = { onConfirm(confirmation.token) },
            onCancel = { onCancel(confirmation.token) },
        )
    }
}

@Composable
fun CartFeedbackBanner(
    message: CartUiMessage,
    onDismiss: () -> Unit,
    onSelectAddress: () -> Unit = {},
) {
    val success = message.kind == CartUiMessageKind.Success
    val foreground = if (success) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        color = if (success) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = ChaskiDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (success) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = foreground,
            )
            Text(
                message.text,
                modifier = Modifier.weight(1f).padding(ChaskiDimens.SpacingSm),
                style = MaterialTheme.typography.bodyMedium,
                color = foreground,
            )
            if (message.action == CartUiMessageAction.SelectAddress) {
                TextButton(onClick = onSelectAddress) { Text("Elegir dirección") }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Descartar aviso", tint = foreground)
            }
        }
    }
}

@Composable
private fun CartStoreChangeDialog(
    confirmation: CartOperationResult.ConfirmationRequired,
    busy: Boolean,
    message: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val incoming = confirmation.incomingItem
    Dialog(onDismissRequest = { if (!busy) onCancel() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(ChaskiDimens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
            ) {
                Text(if (confirmation.offerChanged) "La oferta cambió" else "¿Cambiar de local?",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (confirmation.offerChanged)
                        "Las condiciones de la oferta cambiaron. Revísalas antes de reemplazar el carrito."
                    else "Este producto es de otro local. Si continúas, reemplazará los productos de tu carrito.",
                )
                Text("${incoming.quantity} × ${incoming.productName}", fontWeight = FontWeight.SemiBold)
                Text("Precio provisional por unidad: ${formatCartPrice(incoming.unitPriceMinor)}", color = ChaskiPrice)
                Text("Tu carrito se conserva hasta que confirmes.", style = MaterialTheme.typography.bodySmall)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingSm)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Validando oferta…")
                }
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                ChaskiButton(
                    text = if (confirmation.offerChanged) "Confirmar oferta actualizada" else "Reemplazar carrito",
                    onClick = onConfirm,
                    enabled = !busy,
                )
                ChaskiButton(text = "Conservar mi carrito", onClick = onCancel,
                    enabled = !busy, variant = ChaskiButtonVariant.Outlined)
            }
        }
    }
}
