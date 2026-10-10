package com.chaskifood.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chaskifood.app.ui.theme.ChaskiTheme

@Preview(name = "Chaski — componentes", showBackground = true, widthDp = 360)
@Composable
private fun DesignSystemPreview() = ComponentGallery(false)

@Preview(name = "Chaski — tema oscuro", showBackground = true, widthDp = 360)
@Composable
private fun DarkDesignSystemPreview() = ComponentGallery(true)

@Composable
private fun ComponentGallery(dark: Boolean) {
    ChaskiTheme(darkTheme = dark) {
        Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Chaski Food", style = MaterialTheme.typography.headlineMedium)
                Text("Componentes de la aplicación", style = MaterialTheme.typography.bodyMedium)
                ChaskiTextField(value = "Casa", onValueChange = {}, label = "Dirección de entrega")
                ChaskiTextField(value = "", onValueChange = {}, label = "Nombre", isError = true,
                    supportingText = "Completa este campo")
                ChaskiButton(text = "GUARDAR", onClick = {})
                ChaskiButton(text = "CANCELAR", onClick = {}, variant = ChaskiButtonVariant.Outlined)
                Card { Text("Información guardada", Modifier.padding(16.dp)) }
            }
        }
    }
}
