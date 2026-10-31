package com.chaskifood.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun StoreSelector(
    stores: List<BusinessStore>,
    selectedStoreId: String,
    onSelectStore: (String) -> Unit,
    showStatus: Boolean = false,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val selected = stores.firstOrNull { it.id == selectedStoreId }
    Column {
        Text(
            text = "Local",
            style = MaterialTheme.typography.labelLarge,
            color = ChaskiTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null)
                Text(
                    text = selected?.name?.ifBlank { "Local sin nombre" } ?: "Selecciona un local",
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar local")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                stores.forEach { store ->
                    DropdownMenuItem(
                        text = { Column { Text(store.name.ifBlank { "Local sin nombre" }); if (showStatus) Text(when(store.status) {
                            com.chaskifood.app.feature.business.domain.StoreStatus.ACTIVE -> "Activo"
                            com.chaskifood.app.feature.business.domain.StoreStatus.INACTIVE -> "Inactivo"
                            com.chaskifood.app.feature.business.domain.StoreStatus.SUSPENDED -> "Suspendido"
                        }, style = MaterialTheme.typography.bodySmall) } },
                        onClick = {
                            expanded = false
                            onSelectStore(store.id)
                        },
                    )
                }
            }
        }
    }
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 400)
@Composable private fun StoreSelectorExpandedPreview() {
    com.chaskifood.app.ui.theme.ChaskiTheme {
        StoreSelector(listOf(BusinessStore(id="preview-one",name="Local de muestra"),
            BusinessStore(id="preview-two",name="Local inactivo de muestra",status=com.chaskifood.app.feature.business.domain.StoreStatus.INACTIVE)),
            "preview-one", {}, showStatus=true, initiallyExpanded=true)
    }
}