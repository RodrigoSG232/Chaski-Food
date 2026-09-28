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
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun ManageCatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageCatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ProductCategory?>(null) }

    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

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
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                                    editingCategory = cat
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
                                    editingProduct = prod
                                    showProductDialog = true
                                },
                                onToggleStatus = { id, active ->
                                    viewModel.toggleProductStatus(id, active)
                                },
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (selectedTabIndex == 0) {
                                    editingCategory = null
                                    showCategoryDialog = true
                                } else {
                                    editingProduct = null
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

                    if (showCategoryDialog) {
                        CategoryDialog(
                            businessId = state.businessId,
                            categoryToEdit = editingCategory,
                            onDismiss = { showCategoryDialog = false },
                            onSave = { cat ->
                                viewModel.saveCategory(cat)
                                showCategoryDialog = false
                            },
                        )
                    }

                    if (showProductDialog) {
                        ProductDialog(
                            businessId = state.businessId,
                            categories = state.categories,
                            productToEdit = editingProduct,
                            onDismiss = { showProductDialog = false },
                            onSave = { prod ->
                                viewModel.saveProduct(prod)
                                showProductDialog = false
                            },
                        )
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
                                    text = "S/ ${String.format("%.2f", product.price)}",
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
) {
    var name by remember { mutableStateOf(categoryToEdit?.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (categoryToEdit == null) "Agregar Categoría" else "Editar Categoría")
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
private fun ProductDialog(
    businessId: String,
    categories: List<ProductCategory>,
    productToEdit: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var description by remember { mutableStateOf(productToEdit?.description ?: "") }
    var priceInput by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var prepTimeInput by remember { mutableStateOf(productToEdit?.prepTimeMinutes?.toString() ?: "15") }
    var selectedCategoryId by remember { mutableStateOf(productToEdit?.categoryId ?: categories.firstOrNull()?.id ?: "") }
    var selectedCategoryName by remember { mutableStateOf(productToEdit?.categoryName ?: categories.firstOrNull()?.name ?: "") }
    var imageUrl by remember { mutableStateOf(productToEdit?.imageUrl ?: "") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let {
            val base64Str = it.toBase64DataUri(context)
            if (!base64Str.isNullOrBlank()) {
                imageUrl = base64Str
                capturedBitmap = null
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            capturedBitmap = it
            imageUrl = it.toBase64DataUri()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (productToEdit == null) "Agregar Producto" else "Editar Producto")
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
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Foto capturada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (imageUrl.isNotBlank()) {
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Cámara", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
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
                        onValueChange = { priceInput = it },
                        label = { Text("Precio (S/) *") },
                        placeholder = { Text("32.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
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
                    val price = priceInput.toDoubleOrNull() ?: 0.0
                    val prepTime = prepTimeInput.toIntOrNull() ?: 15
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
        val inputStream = context.contentResolver.openInputStream(this) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        val maxDimension = 500
        val width = bitmap.width
        val height = bitmap.height
        val resizedBitmap = if (width > maxDimension || height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        resizedBitmap.toBase64DataUri(quality)
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
