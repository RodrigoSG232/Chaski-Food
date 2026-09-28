package com.chaskifood.app.feature.business.presentation

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.auth.presentation.AuthSubmitButton
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreStatus
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
fun ManageStoresScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageStoresViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var editingStore by remember { mutableStateOf<BusinessStore?>(null) }
    var showManagerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        when (val state = uiState) {
            is ManageStoresUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is ManageStoresUiState.NoBusinessFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ChaskiDimens.ScreenPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No tienes ningún negocio registrado en la plataforma.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChaskiTextMuted,
                    )
                }
            }
            is ManageStoresUiState.NotApproved -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ChaskiDimens.ScreenPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Tu negocio aún no ha sido APROBADO por el equipo administrador. Una vez aprobado, podrás gestionar tus locales y responsables.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChaskiTextMuted,
                    )
                }
            }
            is ManageStoresUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ChaskiDimens.ScreenPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            is ManageStoresUiState.Success -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Locales (${state.stores.size})") },
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Responsables (${state.managers.size})") },
                        )
                    }

                    if (actionMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = actionMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.clearActionMessage() }) {
                                    Text("OK")
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (selectedTabIndex == 0) {
                            StoresTabContent(
                                stores = state.stores,
                                onEditStore = { store ->
                                    editingStore = store
                                    showStoreDialog = true
                                },
                            )
                        } else {
                            ManagersTabContent(
                                managers = state.managers,
                                stores = state.stores,
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (selectedTabIndex == 0) {
                                    editingStore = null
                                    showStoreDialog = true
                                } else {
                                    showManagerDialog = true
                                }
                            },
                            containerColor = ChaskiPrimary,
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp),
                        ) {
                            Icon(
                                imageVector = if (selectedTabIndex == 0) Icons.Filled.Add else Icons.Filled.PersonAdd,
                                contentDescription = if (selectedTabIndex == 0) "Agregar Local" else "Agregar Responsable",
                            )
                        }
                    }
                }

                if (showStoreDialog) {
                    StoreDialog(
                        businessId = state.business.id,
                        storeToEdit = editingStore,
                        onDismiss = { showStoreDialog = false },
                        onSave = { id, busId, name, address, lat, lng, phone, status ->
                            viewModel.saveStore(
                                id = id,
                                businessId = busId,
                                name = name,
                                address = address,
                                latitudeStr = lat,
                                longitudeStr = lng,
                                phone = phone,
                                status = status,
                                onComplete = { showStoreDialog = false },
                            )
                        },
                    )
                }

                if (showManagerDialog) {
                    ManagerDialog(
                        businessId = state.business.id,
                        availableStores = state.stores,
                        onDismiss = { showManagerDialog = false },
                        onSave = { busId, email, name, phone, assignedIds ->
                            viewModel.assignManager(
                                managerId = "",
                                businessId = busId,
                                email = email,
                                fullName = name,
                                phone = phone,
                                assignedStoreIds = assignedIds,
                                onComplete = { showManagerDialog = false },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoresTabContent(
    stores: List<BusinessStore>,
    onEditStore: (BusinessStore) -> Unit,
) {
    if (stores.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No hay locales registrados aún. Presiona el botón '+' para agregar el primer local de tu negocio.",
                style = MaterialTheme.typography.bodyLarge,
                color = ChaskiTextMuted,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stores.forEach { store ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Store,
                                    contentDescription = null,
                                    tint = ChaskiPrimary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = store.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ChaskiTextPrimary,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (store.status == StoreStatus.ACTIVE) ChaskiStatusApprovedBg else ChaskiStatusRejectedBg,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (store.status == StoreStatus.ACTIVE) "ACTIVO" else "INACTIVO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (store.status == StoreStatus.ACTIVE) ChaskiStatusApprovedFg else ChaskiStatusRejectedFg,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.height(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = store.address,
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                            )
                        }

                        Text(
                            text = "Coordenadas: ${store.latitude}, ${store.longitude}",
                            fontSize = 12.sp,
                            color = ChaskiTextMuted,
                            modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.height(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = store.phone.ifBlank { "Sin teléfono" },
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = { onEditStore(store) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Editar Local",
                                    tint = ChaskiPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagersTabContent(
    managers: List<StoreManager>,
    stores: List<BusinessStore>,
) {
    if (managers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No hay responsables asignados aún. Presiona el botón '+' para asociar un nuevo responsable a tus locales.",
                style = MaterialTheme.typography.bodyLarge,
                color = ChaskiTextMuted,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            managers.forEach { manager ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = manager.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChaskiTextPrimary,
                        )
                        Text(
                            text = manager.email,
                            fontSize = 14.sp,
                            color = ChaskiPrimary,
                        )
                        if (manager.phone.isNotBlank()) {
                            Text(
                                text = "Teléfono: ${manager.phone}",
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Locales autorizados:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChaskiTextMuted,
                        )

                        val assignedNames = stores.filter { store ->
                            manager.assignedStoreIds.contains(store.id)
                        }.map { it.name }

                        if (assignedNames.isEmpty()) {
                            Text(
                                text = "Sin locales asignados",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            assignedNames.forEach { name ->
                                Text(
                                    text = "• $name",
                                    fontSize = 13.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreDialog(
    businessId: String,
    storeToEdit: BusinessStore?,
    onDismiss: () -> Unit,
    onSave: (id: String, businessId: String, name: String, address: String, lat: String, lng: String, phone: String, status: StoreStatus) -> Unit,
) {
    var name by remember { mutableStateOf(storeToEdit?.name ?: "") }
    var address by remember { mutableStateOf(storeToEdit?.address ?: "") }
    var latitude by remember { mutableStateOf(storeToEdit?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(storeToEdit?.longitude?.toString() ?: "") }
    var phone by remember { mutableStateOf(storeToEdit?.phone ?: "") }
    var isActive by remember { mutableStateOf(storeToEdit?.status == StoreStatus.ACTIVE || storeToEdit == null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (storeToEdit == null) "Agregar Local" else "Editar Local")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Local") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitud") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitud") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono de Contacto") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AuthSubmitButton(
                text = "GUARDAR",
                onClick = {
                    onSave(
                        storeToEdit?.id ?: "",
                        businessId,
                        name,
                        address,
                        latitude,
                        longitude,
                        phone,
                        if (isActive) StoreStatus.ACTIVE else StoreStatus.INACTIVE,
                    )
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        },
    )
}

@Composable
private fun ManagerDialog(
    businessId: String,
    availableStores: List<BusinessStore>,
    onDismiss: () -> Unit,
    onSave: (businessId: String, email: String, fullName: String, phone: String, assignedStoreIds: List<String>) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val selectedStoreIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Asignar Responsable")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo del Responsable") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre Completo") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Selecciona los locales autorizados:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (availableStores.isEmpty()) {
                    Text(
                        text = "Primero debes registrar al menos un local.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                } else {
                    availableStores.forEach { store ->
                        val isChecked = selectedStoreIds.contains(store.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedStoreIds.add(store.id)
                                    } else {
                                        selectedStoreIds.remove(store.id)
                                    }
                                },
                            )
                            Text(
                                text = store.name,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AuthSubmitButton(
                text = "ASIGNAR",
                onClick = {
                    onSave(
                        businessId,
                        email,
                        fullName,
                        phone,
                        selectedStoreIds.toList(),
                    )
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR")
            }
        },
    )
}
