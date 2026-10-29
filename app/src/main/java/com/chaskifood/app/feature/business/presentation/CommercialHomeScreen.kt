package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.core.navigation.*
import com.chaskifood.app.feature.business.domain.*
import com.chaskifood.app.ui.components.*
import com.chaskifood.app.ui.theme.*

@Composable
fun CommercialHomeScreen(
    onStores: () -> Unit,
    onCatalog: () -> Unit,
    onHours: (String?, String?) -> Unit,
    onOffers: (String?, String?) -> Unit,
    viewModel: BusinessViewModel = hiltViewModel(),
) {
    val state by viewModel.commercialHome.collectAsStateWithLifecycle()
    val access = LocalNavigationAccess.current
    val current = access?.current()
    val visible = if (current != null && state.origin != current.origin) CommercialHomeState.Loading else state
    fun dispatch(route: String, businessId: String? = null, storeId: String? = null, action: () -> Unit) {
        val origin = visible.origin ?: return
        access?.runIfAllowedFrom(origin, route, businessId, storeId, action = action)
    }
    CommercialHomeContent(visible, viewModel::selectCommercialStore,
        { dispatch(ChaskiDestinations.MANAGE_STORES, action = onStores) },
        { dispatch(ChaskiDestinations.MANAGE_CATALOG, action = onCatalog) },
        { business, store -> dispatch(ChaskiDestinations.STORE_OPERATIONS, business, store) { onHours(business, store) } },
        { business, store -> dispatch(ChaskiDestinations.STORE_PRODUCT_CONFIGURATION, business, store) { onOffers(business, store) } },
        { access?.refresh() })
}

@Composable
internal fun CommercialHomeContent(
    state: CommercialHomeState,
    onSelectStore: (String) -> Unit = {},
    onStores: () -> Unit = {},
    onCatalog: () -> Unit = {},
    onHours: (String?, String?) -> Unit = { _, _ -> },
    onOffers: (String?, String?) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
) {
    when (state) {
        CommercialHomeState.Loading -> LoadingState()
        is CommercialHomeState.Error -> ErrorState(state.message, onRetry)
        else -> Column(Modifier.fillMaxSize().background(ChaskiBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state) {
                is CommercialHomeState.Owner -> {
                    HomeHeading("Mi negocio", "Gestiona tu negocio con tus permisos vigentes")
                    IdentityCard(state.business.businessName.ifBlank { "Negocio sin nombre" },
                        if (state.suspended) "SUSPENDIDO" else "APROBADO", state.suspended) {
                        Text("Autorización: propietario")
                        Text(if (state.suspended) "Conservas tu condición de propietario" else "Negocio propio verificado")
                    }
                    if (state.suspended) {
                        Text("Gestión comercial suspendida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(state.suspensionReason?.takeIf { it.isNotBlank() } ?: "No se ha registrado un motivo de suspensión.")
                        Text("Las funciones de gestión y operación no están disponibles. Puedes utilizar tus opciones personales en Cuenta.")
                    } else {
                        if (state.storeError != null) {
                            Text(state.storeError)
                            OutlinedButton(onClick = onRetry) { Text("Volver a comprobar") }
                        } else if (state.stores?.isEmpty() == true) {
                            Text("Aún no tienes locales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Registra tu primer local para gestionar sus horarios y oferta.")
                        }
                        Text("Gestiona tu negocio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.canManageStores) CommercialRow(Icons.Default.Storefront, "Locales y responsables", "Registra locales y administra responsables", onStores)
                        if (state.canManageCatalog) CommercialRow(Icons.Default.RestaurantMenu, "Catálogo general", "Organiza los productos de tu negocio", onCatalog)
                        if (state.canOperate && !state.stores.isNullOrEmpty()) {
                            CommercialRow(Icons.Default.Schedule, "Horarios de locales", "Gestiona horarios y disponibilidad", { onHours(null, null) })
                            CommercialRow(Icons.Default.Tune, "Oferta y precios por local", "Configura la oferta de cada establecimiento", { onOffers(null, null) })
                        }
                    }
                }
                is CommercialHomeState.Manager -> {
                    HomeHeading(if (state.locals.size > 1) "Mis locales" else "Mi local", "Responsable autorizado")
                    if (state.permissionsChanged) Text("Permisos actualizados. El local anterior ya no está disponible.")
                    val selected = state.locals.singleOrNull { it.store.id == state.selectedId }
                    if (state.locals.size > 1) StoreSelector(state.locals.map { it.store }, state.selectedId.orEmpty(), onSelectStore, showStatus = true)
                    if (selected == null) {
                        Text("No hay locales autorizados disponibles. Vuelve a comprobar tus permisos.")
                        OutlinedButton(onClick = onRetry) { Text("Volver a comprobar") }
                    } else {
                        val store = selected.store
                        IdentityCard(store.name.ifBlank { "Local sin nombre" }, when (store.status) {
                            StoreStatus.ACTIVE -> "ACTIVO"; StoreStatus.INACTIVE -> "INACTIVO"; StoreStatus.SUSPENDED -> "SUSPENDIDO"
                        }, store.status != StoreStatus.ACTIVE) {
                            Text("Asignación: verificada")
                            Text("Estado administrativo: ${when(store.status) { StoreStatus.ACTIVE -> "Activo"; StoreStatus.INACTIVE -> "Inactivo"; StoreStatus.SUSPENDED -> "Suspendido" }}")
                            Text("Estado operativo: ${when(store.operationalStatus) { OperationalStatus.OPEN -> "Abierto"; OperationalStatus.PAUSED -> "Pausado"; OperationalStatus.CLOSED -> "Cerrado" }}")
                        }
                        Text("Horario de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(selected.todayHours)
                        Text(if (selected.withinSchedule) "Dentro del horario de atención" else "Fuera del horario de atención")
                        if (selected.canOperate) {
                            CommercialRow(Icons.Default.Schedule, "Horarios y disponibilidad", "Gestiona este local", { onHours(store.businessId, store.id) })
                            CommercialRow(Icons.Default.Tune, "Oferta y precios", "Productos de este local", { onOffers(store.businessId, store.id) })
                        } else Text("Este local no tiene acciones operativas autorizadas. Su estado administrativo no equivale a una revocación de tu asignación.")
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextMuted)
}

@Composable
private fun IdentityCard(name: String, status: String, restricted: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (restricted) Color(0xFFEDE7F6) else Color(0xFFE8F5E9)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (restricted) Icons.Default.Lock else Icons.Default.Verified, null,
                tint = if (restricted) Color(0xFF4A148C) else Color(0xFF2E7D32))
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CommercialRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = ChaskiPrimary)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ChaskiTextMuted) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// Datos ficticios exclusivos de Preview; no intervienen en consultas productivas.
private val previewOrigin = AccessProvenance(AccessSession("preview-owner", 1), 1, 1)
private fun ownerSample(suspended: Boolean = false, empty: Boolean = false) = CommercialHomeState.Owner(
    previewOrigin, BusinessRequest(id = "preview-business", businessName = "Negocio de muestra"), suspended,
    if (suspended) "Motivo de muestra para revisar el diseño" else null,
    if (empty || suspended) emptyList() else listOf(BusinessStore(id = "preview-store")),
    canManageStores = !suspended, canManageCatalog = !suspended, canOperate = !suspended)
private fun managerSample(multiple: Boolean = false, inactive: Boolean = false) = CommercialHomeState.Manager(previewOrigin,
    listOf(CommercialLocal(BusinessStore(id = "preview-one", name = "Local de muestra", status = if (inactive) StoreStatus.INACTIVE else StoreStatus.ACTIVE,
        operationalStatus = OperationalStatus.CLOSED), !inactive, false, "09:00–18:00")) +
        if (multiple) listOf(CommercialLocal(BusinessStore(id = "preview-two", name = "Segundo local de muestra"), true, true, "08:00–20:00")) else emptyList(), "preview-one")
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun OwnerPreview() { ChaskiTheme { CommercialHomeContent(ownerSample()) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun OwnerEmptyPreview() { ChaskiTheme { CommercialHomeContent(ownerSample(empty = true)) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun OwnerSuspendedPreview() { ChaskiTheme { CommercialHomeContent(ownerSample(suspended = true)) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ManagerPreview() { ChaskiTheme { CommercialHomeContent(managerSample()) } }
@Preview(showBackground = true, widthDp = 320, heightDp = 800, fontScale = 1.3f)
@Composable private fun ManagerMultiplePreview() { ChaskiTheme { CommercialHomeContent(managerSample(multiple = true, inactive = true)) } }