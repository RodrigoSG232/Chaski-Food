package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import android.util.Base64
import com.chaskifood.app.feature.catalog.domain.CatalogRepository
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCatalogRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val access: FirebaseAccessControl,
) : CatalogRepository {

    private val categoriesRef by lazy { firestore.collection("categories") }
    private val productsRef by lazy { firestore.collection("products") }

    override fun getCategories(businessId: String): Flow<ApiResult<List<ProductCategory>>> = callbackFlow {
        if (businessId.isBlank()) {
            trySend(ApiResult.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = categoriesRef.whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Failure(error.localizedMessage ?: "Error al realizar consulta.", error))
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    if (!doc.exists()) return@mapNotNull null
                    val data = doc.data ?: return@mapNotNull null
                    ProductCategory(
                        id = doc.id,
                        businessId = data["businessId"] as? String ?: "",
                        name = data["name"] as? String ?: "",
                        displayOrder = (data["displayOrder"] as? Number)?.toInt() ?: 0,
                        isActive = data["isActive"] as? Boolean ?: true,
                    )
                }?.sortedBy { it.displayOrder } ?: emptyList()

                trySend(ApiResult.Success(list))
            }

        awaitClose { listener.remove() }
    }

    override suspend fun saveCategory(category: ProductCategory): ApiResult<ProductCategory> {
        return try {
            access.requireBusinessOwner(category.businessId)
            require(category.name.isNotBlank()) { "Ingresa el nombre de la categoría." }
            if (category.id.isNotBlank()) {
                check(categoriesRef.document(category.id).get().await().getString("businessId") == category.businessId) { "Categoría ajena al negocio." }
            }
            val docRef = if (category.id.isNotBlank()) categoriesRef.document(category.id) else categoriesRef.document()
            val categoryId = docRef.id
            val newCategory = category.copy(id = categoryId)

            val data = mapOf(
                "businessId" to newCategory.businessId,
                "name" to newCategory.name,
                "displayOrder" to newCategory.displayOrder,
                "isActive" to newCategory.isActive,
            )

            docRef.set(data).await()
            ApiResult.Success(newCategory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al guardar la categoría.", e)
        }
    }

    override suspend fun toggleCategoryStatus(categoryId: String, isActive: Boolean): ApiResult<Unit> {
        return try {
            val businessId = checkNotNull(categoriesRef.document(categoryId).get().await().getString("businessId"))
            access.requireBusinessOwner(businessId)
            categoriesRef.document(categoryId).update("isActive", isActive).await()
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al cambiar el estado de la categoría.", e)
        }
    }

    override fun getProducts(businessId: String): Flow<ApiResult<List<Product>>> = callbackFlow {
        if (businessId.isBlank()) {
            trySend(ApiResult.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = productsRef.whereEqualTo("businessId", businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Failure(error.localizedMessage ?: "Error al realizar consulta.", error))
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    if (!doc.exists()) return@mapNotNull null
                    val data = doc.data ?: return@mapNotNull null
                    Product(
                        id = doc.id,
                        businessId = data["businessId"] as? String ?: "",
                        categoryId = data["categoryId"] as? String ?: "",
                        categoryName = data["categoryName"] as? String ?: "",
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                        imageUrl = data["imageUrl"] as? String,
                        prepTimeMinutes = (data["prepTimeMinutes"] as? Number)?.toInt() ?: 15,
                        isActive = data["isActive"] as? Boolean ?: true,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    )
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                trySend(ApiResult.Success(list))
            }

        awaitClose { listener.remove() }
    }

    override suspend fun saveProduct(product: Product): ApiResult<Product> {
        return try {
            access.requireBusinessOwner(product.businessId)
            require(product.name.isNotBlank() && product.price.isFinite() && product.price > 0 && product.prepTimeMinutes in 1..1440) {
                "Revisa el nombre, precio y tiempo de preparación."
            }
            val category = categoriesRef.document(product.categoryId).get().await()
            check(category.getString("businessId") == product.businessId && category.getBoolean("isActive") == true) {
                "Selecciona una categoría activa de tu negocio."
            }
            if (product.id.isNotBlank()) {
                check(productsRef.document(product.id).get().await().getString("businessId") == product.businessId) { "Producto ajeno al negocio." }
            }
            val docRef = if (product.id.isNotBlank()) productsRef.document(product.id) else productsRef.document()
            val productId = docRef.id
            val source = product.imageUrl.orEmpty()
            val imageUrl = if (source.startsWith("data:image/jpeg;base64,")) {
                val bytes = withContext(Dispatchers.Default) { Base64.decode(source.substringAfter(","), Base64.NO_WRAP) }
                require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "La imagen debe pesar menos de 5 MB." }
                val reference = storage.reference.child("businesses/" + product.businessId + "/products/" + productId + "/" + UUID.randomUUID() + ".jpg")
                reference.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
                reference.downloadUrl.await().toString()
            } else {
                require(source.isBlank() || source.startsWith("https://")) { "Formato de imagen no válido." }
                source
            }
            val newProduct = product.copy(id = productId, imageUrl = imageUrl,
                categoryName = category.getString("name").orEmpty())

            val data = mapOf(
                "businessId" to newProduct.businessId,
                "categoryId" to newProduct.categoryId,
                "categoryName" to newProduct.categoryName,
                "name" to newProduct.name,
                "description" to newProduct.description,
                "price" to newProduct.price,
                "imageUrl" to (newProduct.imageUrl ?: ""),
                "prepTimeMinutes" to newProduct.prepTimeMinutes,
                "isActive" to newProduct.isActive,
                "createdAt" to newProduct.createdAt,
            )

            docRef.set(data).await()
            ApiResult.Success(newProduct)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al guardar el producto.", e)
        }
    }

    override suspend fun toggleProductStatus(productId: String, isActive: Boolean): ApiResult<Unit> {
        return try {
            val businessId = checkNotNull(productsRef.document(productId).get().await().getString("businessId"))
            access.requireBusinessOwner(businessId)
            productsRef.document(productId).update("isActive", isActive).await()
            ApiResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Error al cambiar el estado del producto.", e)
        }
    }
}
