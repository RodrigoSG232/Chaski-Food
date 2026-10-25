package com.chaskifood.app.feature.business.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.address.presentation.AddressMapRequest
import com.chaskifood.app.feature.address.presentation.AddressMapRoute
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import java.util.UUID
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.auth.presentation.AuthSubmitButton
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.StoreManager
import com.chaskifood.app.feature.business.domain.StoreStatus
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
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ManageStoresScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageStoresViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val saving by viewModel.saving.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showStoreDialog by rememberSaveable { mutableStateOf(false) }
    var editingStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var showManagerDialog by rememberSaveable { mutableStateOf(false) }
    var editingManagerId by rememberSaveable { mutableStateOf<String?>(null) }
    val loaded = uiState as? ManageStoresUiState.Success
    val editingStore = loaded?.stores?.find { it.id == editingStoreId }
    val editingManager = loaded?.managers?.find { it.id == editingManagerId }
    val draftStates = rememberSaveableStateHolder()

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = viewModel::loadData) { Text("REINTENTAR") }
                    }
                }
            }
            is ManageStoresUiState.Success -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
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
                                    viewModel.clearActionMessage()
                                    editingStoreId = store.id
                                    draftStates.removeState("store")
                                    showStoreDialog = true
                                },
                            )
                        } else {
                            ManagersTabContent(
                                managers = state.managers,
                                stores = state.stores,
                                onEditManager = { manager ->
                                    viewModel.clearActionMessage()
                                    editingManagerId = manager.id
                                    draftStates.removeState("manager")
                                    showManagerDialog = true
                                },
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                viewModel.clearActionMessage()
                                if (selectedTabIndex == 0) {
                                    editingStoreId = null
                                    draftStates.removeState("store")
                                    showStoreDialog = true
                                } else {
                                    editingManagerId = null
                                    draftStates.removeState("manager")
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

                if (showStoreDialog && (editingStoreId == null || editingStore != null)) {
                    draftStates.SaveableStateProvider("store") {
                        StoreDialog(
                            businessId = state.business.id,
                            storeToEdit = editingStore,
                            saving = saving,
                            message = actionMessage,
                            onDismiss = { if (!saving) { showStoreDialog = false; draftStates.removeState("store") } },
                            onSave = { id, busId, name, imageUrl, address, lat, lng, phone, status ->
                                viewModel.saveStore(
                                    id = id,
                                    businessId = busId,
                                    name = name,
                                    imageUrl = imageUrl,
                                    address = address,
                                    latitudeStr = lat,
                                    longitudeStr = lng,
                                    phone = phone,
                                    status = status,
                                    onComplete = { showStoreDialog = false; draftStates.removeState("store") },
                                )
                            },
                        )
                    }
                }

                if (showManagerDialog && (editingManagerId == null || editingManager != null)) {
                    draftStates.SaveableStateProvider("manager") {
                        ManagerDialog(
                            businessId = state.business.id,
                            availableStores = state.stores,
                            managerToEdit = editingManager,
                            saving = saving,
                            message = actionMessage,
                            onDismiss = { if (!saving) { showManagerDialog = false; draftStates.removeState("manager") } },
                            onSave = { managerId, busId, email, name, phone, assignedIds ->
                                viewModel.assignManager(
                                    managerId = managerId,
                                    businessId = busId,
                                    email = email,
                                    fullName = name,
                                    phone = phone,
                                    assignedStoreIds = assignedIds,
                                    onComplete = { showManagerDialog = false; draftStates.removeState("manager") },
                                )
                            },
                        )
                    }
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Store,
                                    contentDescription = null,
                                    tint = ChaskiPrimary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = store.name,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ChaskiTextPrimary,
                                )
                            }
                            ChaskiStatusPill(
                                text = when (store.status) {
                                    StoreStatus.ACTIVE -> "ACTIVO"
                                    StoreStatus.INACTIVE -> "INACTIVO"
                                    StoreStatus.SUSPENDED -> "SUSPENDIDO"
                                },
                                backgroundColor = if (store.status == StoreStatus.ACTIVE) ChaskiStatusApprovedBg else ChaskiStatusRejectedBg,
                                contentColor = if (store.status == StoreStatus.ACTIVE) ChaskiStatusApprovedFg else ChaskiStatusRejectedFg,
                            )
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
    onEditManager: (StoreManager) -> Unit,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
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
                            }
                            IconButton(onClick = { onEditManager(manager) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Editar Responsable",
                                    tint = ChaskiPrimary,
                                )
                            }
                        }
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
internal fun StoreDialog(
    businessId: String,
    storeToEdit: BusinessStore?,
    saving: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSave: (id: String, businessId: String, name: String, imageUrl: String?, address: String, lat: String, lng: String, phone: String, status: StoreStatus) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.name ?: "") }
    var address by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.address ?: "") }
    var latitude by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.latitude?.toString() ?: "") }
    var longitude by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.longitude?.toString() ?: "") }
    var phone by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.phone ?: "") }
    var isActive by rememberSaveable(storeToEdit?.id) { mutableStateOf(storeToEdit?.status == StoreStatus.ACTIVE || storeToEdit == null) }
    var photoSource by rememberSaveable(storeToEdit?.id) { mutableStateOf<String?>(null) }
    var imageUrl by remember(storeToEdit?.id) { mutableStateOf(storeToEdit?.imageUrl) }
    var imageLoading by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photoSource) {
        val source = photoSource ?: return@LaunchedEffect
        imageLoading = true
        imageError = null
        val encoded = withContext(Dispatchers.IO) { Uri.parse(source).toStoreImageDataUri(context) }
        if (encoded == null) imageError = "No se pudo procesar la imagen seleccionada."
        else imageUrl = encoded
        imageLoading = false
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) photoSource = uri.toString()
    }

    var showMapPicker by rememberSaveable { mutableStateOf(false) }

    if (showMapPicker) {
        val mapRequestId = rememberSaveable { "store_map_${UUID.randomUUID()}" }
        Dialog(
            onDismissRequest = { showMapPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val initialCoords = remember(latitude, longitude) {
                    val lat = latitude.toDoubleOrNull()
                    val lng = longitude.toDoubleOrNull()
                    if (lat != null && lng != null) AddressCoordinates(lat, lng).takeIf { it.isValid } else null
                } ?: AddressCoordinates(-12.046374, -77.042793)

                AddressMapRoute(
                    request = AddressMapRequest(
                        id = mapRequestId,
                        initialPoint = initialCoords,
                    ),
                    addressText = address,
                    onCancel = { showMapPicker = false },
                    onConfirm = { point, suggestedAddress ->
                        latitude = point.latitude.toString()
                        longitude = point.longitude.toString()
                        if (!suggestedAddress.isNullOrBlank()) {
                            address = suggestedAddress
                        }
                        showMapPicker = false
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = if (storeToEdit == null) "Agregar Local" else "Editar Local")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving || imageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Imagen o logo del local", fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = storeImageModel(imageUrl),
                            contentDescription = "Imagen del local",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !saving && !imageLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageUrl.isNullOrBlank()) "Seleccionar imagen" else "Cambiar imagen")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Local *") },
                    placeholder = { Text("Ej. Local Central - Miraflores") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección *") },
                    placeholder = { Text("Ej. Av. Larco 123, Lima") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Ubicación en el Mapa *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedButton(
                        onClick = { showMapPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ChaskiPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChaskiPrimary),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = ChaskiPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (latitude.isNotBlank() && longitude.isNotBlank())
                                "Cambiar ubicación en el mapa"
                            else
                                "Seleccionar ubicación en el mapa",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            softWrap = true,
                        )
                    }

                    if (latitude.isNotBlank() && longitude.isNotBlank()) {
                        Text(
                            text = "Coordenadas fijadas: $latitude, $longitude",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChaskiTextMuted,
                        )
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { char -> char.isDigit() }.take(12) },
                    label = { Text("Teléfono de Contacto *") },
                    placeholder = { Text("Ej. 987654321") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (storeToEdit?.status == StoreStatus.SUSPENDED) {
                    Text("Local suspendido por administración.", color = MaterialTheme.colorScheme.error)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Local activo", modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it }, enabled = !saving)
                }
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
                        imageUrl,
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

private fun Uri.toStoreImageDataUri(context: Context): String? {
    return try {
        val maxDimension = 1200
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(this)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(this)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        ) else bitmap
        try {
            val output = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 82, output)
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()
        }
    } catch (_: Exception) { null }
}

private fun storeImageModel(value: String?): Any? {
    if (value.isNullOrBlank() || !value.startsWith("data:image")) return value
    return runCatching {
        val bytes = Base64.decode(value.substringAfter(","), Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun ManagerDialog(
    businessId: String,
    availableStores: List<BusinessStore>,
    managerToEdit: StoreManager?,
    saving: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSave: (managerId: String, businessId: String, email: String, fullName: String, phone: String, assignedStoreIds: List<String>) -> Unit,
) {
    var email by rememberSaveable(managerToEdit?.id) { mutableStateOf(managerToEdit?.email ?: "") }
    var fullName by rememberSaveable(managerToEdit?.id) { mutableStateOf(managerToEdit?.fullName ?: "") }
    var phone by rememberSaveable(managerToEdit?.id) { mutableStateOf(managerToEdit?.phone ?: "") }
    var selectedStoreIds by rememberSaveable(managerToEdit?.id,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })) {
        mutableStateOf(managerToEdit?.assignedStoreIds.orEmpty().toList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Asignar Responsable")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
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
                    placeholder = { Text("Ej. responsable@negocio.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre Completo") },
                    placeholder = { Text("Ej. Carlos Mendoza") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono (Opcional)") },
                    placeholder = { Text("Ej. +51 912345678") },
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
                                        selectedStoreIds = (selectedStoreIds + store.id).distinct()
                                    } else {
                                        selectedStoreIds = selectedStoreIds - store.id
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
                text = "GUARDAR",
                onClick = {
                    onSave(
                        managerToEdit?.id ?: "",
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
