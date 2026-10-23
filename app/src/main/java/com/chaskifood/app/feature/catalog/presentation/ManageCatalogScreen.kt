@file:OptIn(ExperimentalCoroutinesApi::class)

package com.chaskifood.app.feature.catalog.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.core.net.toUri
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import java.io.File
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.auth.presentation.AuthSubmitButton
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.formatProductPrice
import com.chaskifood.app.feature.catalog.domain.parseProductPrice
import androidx.compose.ui.focus.onFocusChanged
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedBg
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedFg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedBg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ManageCatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageCatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    var showProductDialog by rememberSaveable { mutableStateOf(false) }
    var editingProductId by rememberSaveable { mutableStateOf<String?>(null) }
    val loaded = uiState as? ManageCatalogUiState.Success
    val editingCategory = loaded?.categories?.find { it.id == editingCategoryId }
    val editingProduct = loaded?.products?.find { it.id == editingProductId }
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
            is ManageCatalogUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is ManageCatalogUiState.NoBusinessFound -> {
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
            is ManageCatalogUiState.NotApproved -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ChaskiDimens.ScreenPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Tu negocio aún no está aprobado para gestionar catálogo de productos.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChaskiTextMuted,
                    )
                }
            }
            is ManageCatalogUiState.Error -> {
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
                        )
                        TextButton(onClick = viewModel::retryLoading) { Text("REINTENTAR") }
                    }
                }
            }
            is ManageCatalogUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = ChaskiDimens.ScreenPadding)) {
                        Text(
                            text = "Menú y Productos",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D0D0D),
                        )
                        Text(
                            text = state.businessName,
                            fontSize = 14.sp,
                            color = Color(0xFF757575),
                        )
                        Spacer(Modifier.height(ChaskiDimens.SpacingLg))
                    }

                    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Categorías (${state.categories.size})") },
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Productos (${state.products.size})") },
                        )
                    }

                    if (actionMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = actionMessage!!,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                TextButton(onClick = { viewModel.clearActionMessage() }) {
                                    Text("OK")
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (selectedTabIndex == 0) {
                            // Pestaña Categorías
                            CategoriesTabContent(
                                categories = state.categories,
                                onEditCategory = { cat ->
                                    viewModel.clearSaveError()
                                    editingCategoryId = cat.id
                                    draftStates.removeState("category")
                                    showCategoryDialog = true
                                },
                                onToggleStatus = { id, active ->
                                    viewModel.toggleCategoryStatus(id, active)
                                },
                            )
                        } else {
                            // Pestaña Productos
                            ProductsTabContent(
                                products = state.products,
                                onEditProduct = { prod ->
                                    viewModel.clearSaveError()
                                    editingProductId = prod.id
                                    draftStates.removeState("product")
                                    showProductDialog = true
                                },
                                onToggleStatus = { id, active ->
                                    viewModel.toggleProductStatus(id, active)
                                },
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                viewModel.clearSaveError()
                                if (selectedTabIndex == 0) {
                                    editingCategoryId = null
                                    draftStates.removeState("category")
                                    showCategoryDialog = true
                                } else {
                                    editingProductId = null
                                    draftStates.removeState("product")
                                    showProductDialog = true
                                }
                            },
                            containerColor = ChaskiPrimary,
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar")
                        }
                    }

                    if (showCategoryDialog && (editingCategoryId == null || editingCategory != null)) {
                        draftStates.SaveableStateProvider("category") {
                            CategoryDialog(
                                businessId = state.businessId,
                                categoryToEdit = editingCategory,
                                saving = saving, saveError = saveError,
                                onDismiss = { if (!saving) { showCategoryDialog = false; draftStates.removeState("category") } },
                                onSave = { cat ->
                                    viewModel.saveCategory(cat) { showCategoryDialog = false; draftStates.removeState("category") }
                                },
                            )
                        }
                    }

                    if (showProductDialog && (editingProductId == null || editingProduct != null)) {
                        draftStates.SaveableStateProvider("product") {
                            ProductDialog(
                                businessId = state.businessId,
                                categories = state.categories,
                                productToEdit = editingProduct,
                                saving = saving, saveError = saveError,
                                onDismiss = { if (!saving) { showProductDialog = false; draftStates.removeState("product") } },
                                onSave = { prod ->
                                    viewModel.saveProduct(prod) { showProductDialog = false; draftStates.removeState("product") }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesTabContent(
    categories: List<ProductCategory>,
    onEditCategory: (ProductCategory) -> Unit,
    onToggleStatus: (String, Boolean) -> Unit,
) {
    if (categories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aún no has creado categorías para tu menú.",
                color = ChaskiTextMuted,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
            contentPadding = PaddingValues(ChaskiDimens.ScreenPadding),
        ) {
            items(categories, key = { it.id }) { category ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ChaskiTextPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (category.isActive) "Estado: Activa" else "Estado: Desactivada",
                                fontSize = 13.sp,
                                color = if (category.isActive) ChaskiStatusApprovedFg else ChaskiStatusRejectedFg,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = category.isActive,
                                onCheckedChange = { active -> onToggleStatus(category.id, active) },
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { onEditCategory(category) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsTabContent(
    products: List<Product>,
    onEditProduct: (Product) -> Unit,
    onToggleStatus: (String, Boolean) -> Unit,
) {
    if (products.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aún no has registrado productos en tu catálogo.",
                color = ChaskiTextMuted,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
            contentPadding = PaddingValues(ChaskiDimens.ScreenPadding),
        ) {
            items(products, key = { it.id }) { product ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Imagen de Producto
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEEEEEE)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!product.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = rememberImageModel(product.imageUrl),
                                    contentDescription = product.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null,
                                    tint = Color(0xFF9E9E9E),
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = ChaskiTextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "S/ ${formatProductPrice(product.price)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = ChaskiPrimary,
                                )
                            }

                            if (product.description.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = product.description,
                                    fontSize = 13.sp,
                                    color = Color(0xFF757575),
                                    maxLines = 2,
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    color = Color(0xFFE3F2FD),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = product.categoryName.ifBlank { "General" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF757575),
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "${product.prepTimeMinutes} min",
                                        fontSize = 12.sp,
                                        color = Color(0xFF757575),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = product.isActive,
                                onCheckedChange = { active -> onToggleStatus(product.id, active) },
                            )
                            IconButton(onClick = { onEditProduct(product) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Editar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    businessId: String,
    categoryToEdit: ProductCategory?,
    onDismiss: () -> Unit,
    onSave: (ProductCategory) -> Unit,
    saving: Boolean,
    saveError: String?,
) {
    var name by rememberSaveable(categoryToEdit?.id) { mutableStateOf(categoryToEdit?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(text = if (categoryToEdit == null) "Agregar Categoría" else "Editar Categoría")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la Categoría *") },
                    placeholder = { Text("Ej. Platos de Fondo, Bebidas, Postres") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AuthSubmitButton(
                text = "GUARDAR",
                onClick = {
                    if (saving) return@AuthSubmitButton
                    if (name.isNotBlank()) {
                        val category = categoryToEdit?.copy(name = name.trim())
                            ?: ProductCategory(businessId = businessId, name = name.trim())
                        onSave(category)
                    }
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCELAR")
            }
        },
    )
}

@Composable
internal fun ProductDialog(
    businessId: String,
    categories: List<ProductCategory>,
    productToEdit: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    saving: Boolean,
    saveError: String?,
) {
    val context = LocalContext.current
    var name by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.name ?: "") }
    var description by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.description ?: "") }
    var priceInput by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.price?.let(::formatProductPrice) ?: "") }
    var priceTouched by rememberSaveable(productToEdit?.id) { mutableStateOf(false) }
    val parsedPrice = remember(priceInput) { parseProductPrice(priceInput) }
    var prepTimeInput by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.prepTimeMinutes?.toString() ?: "15") }
    var selectedCategoryId by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.categoryId ?: categories.firstOrNull()?.id ?: "") }
    var selectedCategoryName by rememberSaveable(productToEdit?.id) { mutableStateOf(productToEdit?.categoryName ?: categories.firstOrNull()?.name ?: "") }
    // Save only the source URI. The bitmap/base64 stays out of the activity Bundle.
    var photoSource by rememberSaveable(productToEdit?.id) { mutableStateOf<String?>(null) }
    var photoRevision by rememberSaveable(productToEdit?.id) { mutableIntStateOf(0) }
    var imageUrl by remember { mutableStateOf(productToEdit?.imageUrl ?: "") }
    var imageLoading by remember { mutableStateOf(photoSource != null) }
    var imageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photoSource, photoRevision) {
        val source = photoSource ?: return@LaunchedEffect
        imageLoading = true
        imageError = null
        try {
            val encoded = withContext(Dispatchers.IO) { source.toUri().toBase64DataUri(context) }
            if (!encoded.isNullOrBlank()) imageUrl = encoded
            else imageError = "No se pudo leer la foto del borrador. Selecciona otra fotografía."
        } finally { imageLoading = false }
    }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            imageLoading = true
            imageError = null
            try { context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* The current grant still allows reading this selection. */ }
            photoSource = selectedUri.toString()
            photoRevision += 1
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val photo = File.createTempFile("product-draft-", ".jpg", context.cacheDir)
                photo.outputStream().use { output -> check(it.compress(Bitmap.CompressFormat.JPEG, 90, output)) }
                imageLoading = true
                photoSource = Uri.fromFile(photo).toString()
                photoRevision += 1
                imageError = null
            } catch (_: Exception) { imageError = "No se pudo conservar la foto. Intenta tomarla nuevamente." }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(text = if (productToEdit == null) "Agregar Producto" else "Editar Producto")
                imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving || imageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Sección Foto de Producto
                Text(
                    text = "Fotografía del Producto",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF424242),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = rememberImageModel(imageUrl),
                            contentDescription = "Foto seleccionada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFF9E9E9E),
                            )
                            Text(
                                text = "Sin fotografía seleccionada",
                                fontSize = 12.sp,
                                color = Color(0xFF757575),
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        enabled = !saving && !imageLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Cámara", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        enabled = !saving && !imageLoading,
                        onClick = {
                            if (saving) return@OutlinedButton
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Galería", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto *") },
                    placeholder = { Text("Ej. Lomo Saltado Criollo") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    placeholder = { Text("Ej. Trozos de lomo de res salteados al wok con cebolla y tomate.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it; priceTouched = true },
                        enabled = !saving,
                        isError = priceTouched && parsedPrice.error != null,
                        supportingText = {
                            Text(if (priceTouched) parsedPrice.error ?: "Hasta dos decimales." else "Acepta punto o coma y hasta dos decimales.")
                        },
                        label = { Text("Precio (S/) *") },
                        placeholder = { Text("32.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).onFocusChanged { focus ->
                            if (!focus.isFocused && priceTouched) parseProductPrice(priceInput).value?.let { priceInput = formatProductPrice(it) }
                        },
                    )

                    OutlinedTextField(
                        value = prepTimeInput,
                        onValueChange = { prepTimeInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Preparación (min) *") },
                        placeholder = { Text("20") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Categoría Desplegable
                Text(
                    text = "Categoría *",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF424242),
                )

                if (categories.isEmpty()) {
                    Text(
                        text = "⚠️ Primero debes crear al menos una categoría.",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCategoryName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedCategoryDropdown = !expandedCategoryDropdown }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedCategoryDropdown = !expandedCategoryDropdown },
                        )

                        DropdownMenu(
                            expanded = expandedCategoryDropdown,
                            onDismissRequest = { expandedCategoryDropdown = false },
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        if (saving) return@DropdownMenuItem
                                        selectedCategoryId = cat.id
                                        selectedCategoryName = cat.name
                                        expandedCategoryDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AuthSubmitButton(
                text = "GUARDAR",
                onClick = {
                    if (saving || imageLoading || imageError != null) return@AuthSubmitButton
                    priceTouched = true
                    val price = parseProductPrice(priceInput).value ?: return@AuthSubmitButton
                    priceInput = formatProductPrice(price)
                    val prepTime = prepTimeInput.toIntOrNull() ?: 0
                    val product = productToEdit?.copy(
                        name = name.trim(),
                        description = description.trim(),
                        price = price,
                        categoryId = selectedCategoryId,
                        categoryName = selectedCategoryName,
                        imageUrl = imageUrl,
                        prepTimeMinutes = prepTime,
                    ) ?: Product(
                        businessId = businessId,
                        categoryId = selectedCategoryId,
                        categoryName = selectedCategoryName,
                        name = name.trim(),
                        description = description.trim(),
                        price = price,
                        imageUrl = imageUrl,
                        prepTimeMinutes = prepTime,
                    )
                    onSave(product)
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCELAR")
            }
        },
    )
}

private fun Bitmap.toBase64DataUri(quality: Int = 70): String {
    val outputStream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val byteArray = outputStream.toByteArray()
    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}

private fun Uri.toBase64DataUri(context: Context, quality: Int = 70): String? {
    return try {
        val maxDimension = 500
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(this) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / sampleSize > maxDimension * 2) sampleSize *= 2
        options.inSampleSize = sampleSize
        options.inJustDecodeBounds = false
        val bitmap = context.contentResolver.openInputStream(this)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val resizedBitmap = if (width > maxDimension || height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        try { resizedBitmap.toBase64DataUri(quality) } finally {
            if (resizedBitmap !== bitmap) resizedBitmap.recycle()
            bitmap.recycle()
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun rememberImageModel(imageUrl: String?): Any? {
    return remember(imageUrl) {
        if (imageUrl.isNullOrBlank()) null
        else if (imageUrl.startsWith("data:image")) {
            try {
                val base64Data = imageUrl.substringAfter(",")
                val decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                imageUrl
            }
        } else {
            imageUrl
        }
    }
}
