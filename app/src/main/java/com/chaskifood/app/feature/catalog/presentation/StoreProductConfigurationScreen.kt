package com.chaskifood.app.feature.catalog.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.formatProductPrice
import com.chaskifood.app.feature.catalog.domain.parseProductPrice
import com.chaskifood.app.feature.catalog.domain.storeProductConfigurationId
import com.chaskifood.app.ui.components.ChaskiStatusPill
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedBg
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedFg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedBg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun StoreProductConfigurationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreProductConfigurationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val operations by viewModel.operations.collectAsState()

    StoreProductConfigurationContent(
        state = state,
        operations = operations,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSelectStore = viewModel::selectStore,
        onSave = viewModel::saveConfiguration,
        onClearFeedback = viewModel::clearOperationFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun StoreProductConfigurationContent(
    state: StoreProductConfigurationUiState,
    operations: Map<String, ProductConfigurationOperationState>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectStore: (String) -> Unit,
    onSave: (String, Double, StoreProductAvailability) -> Unit,
    onClearFeedback: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ChaskiDimens.ScreenPadding),
        ) {
            Text(
                text = "Productos por local",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingXs))
            Text(
                text = "Configura el precio y la disponibilidad para cada establecimiento.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChaskiTextMuted,
            )
            Spacer(Modifier.height(ChaskiDimens.SpacingLg))

            when (state) {
                StoreProductConfigurationUiState.Loading -> CenteredLoading()
                StoreProductConfigurationUiState.SignedOut -> StateMessage(
                    "Inicia sesión para configurar productos por local.",
                    onRetry,
                )
                is StoreProductConfigurationUiState.BusinessNotApproved -> StateMessage(
                    businessStatusMessage(state.status),
                    onRetry,
                )
                StoreProductConfigurationUiState.NoStores -> StateMessage(
                    "No tienes locales autorizados para configurar.",
                    onRetry,
                )
                is StoreProductConfigurationUiState.StoreNotEnabled -> {
                    StoreSelector(state.stores, state.selectedStoreId, onSelectStore)
                    StateMessage("El local seleccionado no está habilitado para configurar su oferta.", onRetry)
                }
                is StoreProductConfigurationUiState.LoadingProducts -> {
                    StoreSelector(state.stores, state.selectedStoreId, onSelectStore)
                    CenteredLoading()
                }
                is StoreProductConfigurationUiState.Error -> {
                    if (state.stores.isNotEmpty() && state.selectedStoreId != null) {
                        StoreSelector(state.stores, state.selectedStoreId, onSelectStore)
                    }
                    StateMessage(state.message, onRetry)
                }
                is StoreProductConfigurationUiState.Success -> {
                    StoreSelector(state.stores, state.selectedStoreId, onSelectStore)
                    Spacer(Modifier.height(ChaskiDimens.SpacingMd))
                    if (state.products.isEmpty()) {
                        StateMessage("Este negocio todavía no tiene productos registrados.", onRetry = null)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                        ) {
                            items(state.products, key = { it.product.id }) { item ->
                                val key = storeProductConfigurationId(state.selectedStoreId, item.product.id)
                                ProductConfigurationCard(
                                    storeId = state.selectedStoreId,
                                    item = item,
                                    operation = operations[key] ?: ProductConfigurationOperationState(),
                                    onSave = { price, availability ->
                                        onSave(item.product.id, price, availability)
                                    },
                                    onClearFeedback = {
                                        onClearFeedback(state.selectedStoreId, item.product.id)
                                    },
                                )
                            }
                            item { Spacer(Modifier.height(ChaskiDimens.SpacingLg)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreSelector(
    stores: List<BusinessStore>,
    selectedStoreId: String,
    onSelectStore: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                        text = { Text(store.name.ifBlank { "Local sin nombre" }) },
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

@Composable
internal fun ProductConfigurationCard(
    storeId: String,
    item: StoreProductItem,
    operation: ProductConfigurationOperationState,
    onSave: (Double, StoreProductAvailability) -> Unit,
    onClearFeedback: () -> Unit,
) {
    var priceInput by rememberSaveable(storeId, item.product.id) { mutableStateOf(formatProductPrice(item.price)) }
    var availability by rememberSaveable(storeId, item.product.id) { mutableStateOf(item.availability) }
    var priceTouched by rememberSaveable(storeId, item.product.id) { mutableStateOf(false) }
    var draftChanged by rememberSaveable(storeId, item.product.id) { mutableStateOf(false) }
    val configurationSignature = "${item.configuration?.price}:${item.configuration?.availability}"
    var observedSignature by rememberSaveable(storeId, item.product.id) { mutableStateOf(configurationSignature) }
    var acknowledgedRevision by rememberSaveable(storeId, item.product.id) {
        mutableStateOf(operation.successRevision)
    }
    val parsedPrice = remember(priceInput) { parseProductPrice(priceInput) }

    LaunchedEffect(configurationSignature) {
        if (configurationSignature != observedSignature) {
            if (!draftChanged && !operation.saving) {
                priceInput = formatProductPrice(item.price)
                availability = item.availability
                priceTouched = false
            }
            observedSignature = configurationSignature
        }
    }
    LaunchedEffect(operation.successRevision) {
        if (operation.successRevision > acknowledgedRevision) {
            acknowledgedRevision = operation.successRevision
            draftChanged = false
            priceTouched = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChaskiTextPrimary,
                    )
                    Text(
                        text = item.product.categoryName.ifBlank { "Sin categoría" },
                        style = MaterialTheme.typography.bodySmall,
                        color = ChaskiTextMuted,
                    )
                }
                ChaskiStatusPill(
                    text = if (item.isConfigured) "CONFIGURADO" else "SIN CONFIGURAR",
                    backgroundColor = if (item.isConfigured) ChaskiStatusApprovedBg else ChaskiStatusRejectedBg,
                    contentColor = if (item.isConfigured) ChaskiStatusApprovedFg else ChaskiStatusRejectedFg,
                )
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = priceInput,
                onValueChange = {
                    priceInput = it
                    priceTouched = true
                    draftChanged = true
                    onClearFeedback()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !operation.saving,
                label = { Text("Precio en este local (S/)") },
                singleLine = true,
                isError = priceTouched && parsedPrice.error != null,
                supportingText = {
                    Text(
                        if (priceTouched) parsedPrice.error ?: "Hasta dos decimales."
                        else "Acepta punto o coma y hasta dos decimales.",
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Disponible para nuevas compras", fontWeight = FontWeight.Medium)
                    Text(
                        if (availability == StoreProductAvailability.AVAILABLE) "AVAILABLE" else "OUT_OF_STOCK",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChaskiTextMuted,
                    )
                }
                Switch(
                    checked = availability == StoreProductAvailability.AVAILABLE,
                    onCheckedChange = {
                        availability = if (it) StoreProductAvailability.AVAILABLE
                        else StoreProductAvailability.OUT_OF_STOCK
                        draftChanged = true
                        onClearFeedback()
                    },
                    enabled = !operation.saving,
                    modifier = Modifier.semantics {
                        contentDescription = "Disponibilidad de ${item.product.name}"
                    },
                )
            }

            operation.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            operation.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ChaskiStatusApprovedFg, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    priceTouched = true
                    val price = parseProductPrice(priceInput).value ?: return@Button
                    priceInput = formatProductPrice(price)
                    onSave(price, availability)
                },
                enabled = !operation.saving && parsedPrice.value != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (operation.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("GUARDAR CONFIGURACIÓN")
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ChaskiPrimary)
    }
}

@Composable
private fun StateMessage(message: String, onRetry: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = ChaskiTextMuted)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("REINTENTAR") }
        }
    }
}

private fun businessStatusMessage(status: BusinessStatus): String = when (status) {
    BusinessStatus.PENDING_REVIEW -> "Tu negocio todavía está pendiente de aprobación."
    BusinessStatus.OBSERVED -> "Tu solicitud tiene observaciones pendientes."
    BusinessStatus.REJECTED -> "La solicitud del negocio fue rechazada."
    BusinessStatus.SUSPENDED -> "El negocio está suspendido y no puede configurar su oferta."
    BusinessStatus.APPROVED -> "No se encontraron locales autorizados."
}
