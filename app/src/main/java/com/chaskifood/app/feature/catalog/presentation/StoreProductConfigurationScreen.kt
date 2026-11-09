package com.chaskifood.app.feature.catalog.presentation

import com.chaskifood.app.ui.components.StoreSelector
import com.chaskifood.app.core.navigation.LocalProtectedAction

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
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
    val authorize = LocalProtectedAction.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()

    StoreProductConfigurationContent(
        state = state,
        drafts = drafts,
        bulk = bulk,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSelectStore = { id -> authorize { viewModel.selectStore(id) } },
        onPriceChange = viewModel::editPrice,
        onAvailabilityChange = viewModel::editAvailability,
        onPromotionChange = viewModel::editPromotion,
        onSaveAll = { authorize { viewModel.saveAll() } },
        onConfirmStoreSwitch = { authorize { viewModel.confirmStoreSwitch() } },
        onCancelStoreSwitch = viewModel::cancelStoreSwitch,
        modifier = modifier,
    )
}

@Composable
internal fun StoreProductConfigurationContent(
    state: StoreProductConfigurationUiState,
    drafts: Map<String, ProductConfigurationDraft>,
    bulk: ProductConfigurationBulkState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectStore: (String) -> Unit,
    onPriceChange: (String, String) -> Unit,
    onAvailabilityChange: (String, StoreProductAvailability) -> Unit,
    onPromotionChange: (String, Boolean) -> Unit,
    onSaveAll: () -> Unit,
    onConfirmStoreSwitch: () -> Unit,
    onCancelStoreSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bulk.pendingStoreId != null) AlertDialog(
        onDismissRequest = onCancelStoreSwitch,
        title = { Text("Cambios sin guardar") },
        text = { Text("Si cambias de local, se descartarán los cambios pendientes de este local.") },
        confirmButton = { TextButton(onClick = onConfirmStoreSwitch) { Text("Descartar y cambiar") } },
        dismissButton = { TextButton(onClick = onCancelStoreSwitch) { Text("Seguir editando") } },
    )
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
                        val pending = pendingProductIds(state.products, drafts)
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingMd),
                        ) {
                            items(state.products, key = { it.product.id }) { item ->
                                ProductConfigurationCard(
                                    storeId = state.selectedStoreId,
                                    item = item,
                                    draft = drafts[item.product.id]
                                        ?: ProductConfigurationDraft(formatProductPrice(item.price),
                                            item.availability, item.showInPromotions),
                                    dirty = item.product.id in pending,
                                    invalid = item.product.id in bulk.invalidProductIds,
                                    saving = bulk.saving,
                                    onPriceChange = { onPriceChange(item.product.id, it) },
                                    onAvailabilityChange = { onAvailabilityChange(item.product.id, it) },
                                    onPromotionChange = { onPromotionChange(item.product.id, it) },
                                )
                            }
                            item { Spacer(Modifier.height(ChaskiDimens.SpacingLg)) }
                        }
                        Text("${pending.size} productos con cambios pendientes", style = MaterialTheme.typography.bodySmall)
                        bulk.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        bulk.message?.let { Text(it, color = ChaskiStatusApprovedFg) }
                        Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                        Button(onClick = onSaveAll, enabled = pending.isNotEmpty() && !bulk.saving,
                            modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            if (bulk.saving) CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("GUARDAR CAMBIOS")
                        }
                        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductConfigurationCard(
    storeId: String,
    item: StoreProductItem,
    draft: ProductConfigurationDraft,
    dirty: Boolean,
    invalid: Boolean,
    saving: Boolean,
    onPriceChange: (String) -> Unit,
    onAvailabilityChange: (StoreProductAvailability) -> Unit,
    onPromotionChange: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable(storeId, item.product.id) { mutableStateOf(false) }
    val parsedPrice = remember(draft.priceInput) { parseProductPrice(draft.priceInput) }
    LaunchedEffect(invalid) { if (invalid) expanded = true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                .semantics { contentDescription = if (expanded) "Contraer ${item.product.name}" else "Expandir ${item.product.name}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Text("S/ ${parsedPrice.value?.let(::formatProductPrice) ?: draft.priceInput} · " +
                        if (draft.availability == StoreProductAvailability.AVAILABLE) "Disponible" else "Agotado",
                        style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted)
                    if (draft.showInPromotions) Text("En promociones",
                        style = MaterialTheme.typography.bodySmall, color = ChaskiPrimary)
                    if (dirty) Text("Cambios sin guardar", style = MaterialTheme.typography.bodySmall,
                        color = ChaskiPrimary)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null)
            }
            Spacer(Modifier.height(8.dp))
            ChaskiStatusPill(
                text = if (item.isConfigured) "CONFIGURADO" else "SIN CONFIGURAR",
                backgroundColor = if (item.isConfigured) ChaskiStatusApprovedBg else ChaskiStatusRejectedBg,
                contentColor = if (item.isConfigured) ChaskiStatusApprovedFg else ChaskiStatusRejectedFg,
            )
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = draft.priceInput,
                    onValueChange = onPriceChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    label = { Text("Precio en este local (S/)") },
                    singleLine = true,
                    isError = invalid || parsedPrice.error != null,
                    supportingText = { Text(parsedPrice.error ?: "Acepta punto o coma y hasta dos decimales.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Disponible para nuevas compras", modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium)
                    Switch(
                        checked = draft.availability == StoreProductAvailability.AVAILABLE,
                        onCheckedChange = {
                            onAvailabilityChange(if (it) StoreProductAvailability.AVAILABLE
                                else StoreProductAvailability.OUT_OF_STOCK)
                        },
                        enabled = !saving,
                        modifier = Modifier.semantics {
                            contentDescription = "Disponibilidad de ${item.product.name}"
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mostrar en promociones", modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium)
                    Switch(
                        checked = draft.showInPromotions,
                        onCheckedChange = onPromotionChange,
                        enabled = !saving,
                        modifier = Modifier.semantics {
                            contentDescription = "Promociones de ${item.product.name}"
                        },
                    )
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun StoreProductConfigurationPreview() {
    com.chaskifood.app.ui.theme.ChaskiTheme {
        StoreProductConfigurationContent(StoreProductConfigurationUiState.NoStores, emptyMap(),
            ProductConfigurationBulkState(), {}, {}, {}, { _, _ -> }, { _, _ -> },
            { _, _ -> }, {}, {}, {})
    }
}
