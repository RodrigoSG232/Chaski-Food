package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.firebase.FirebaseAccessControl
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.StoreProductConfigurationRepository
import com.chaskifood.app.feature.catalog.domain.isValid
import com.chaskifood.app.feature.catalog.domain.storeProductConfigurationId
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStoreProductConfigurationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val access: FirebaseAccessControl,
) : StoreProductConfigurationRepository {

    private val configurationsRef by lazy { firestore.collection("store_product_configs") }
    private val storesRef by lazy { firestore.collection("stores") }
    private val productsRef by lazy { firestore.collection("products") }

    override fun getConfigurations(
        storeId: String,
    ): Flow<ApiResult<List<StoreProductConfiguration>>> = callbackFlow {
        if (storeId.isBlank()) {
            trySend(ApiResult.Failure("Selecciona un local válido."))
            close()
            return@callbackFlow
        }

        val businessId = try {
            requireAuthorizedStore(storeId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trySend(ApiResult.Failure(error.localizedMessage ?: "No se pudo autorizar el local.", error))
            close()
            return@callbackFlow
        }

        val listener = configurationsRef.whereEqualTo("storeId", storeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Failure(error.localizedMessage ?: "No se pudo cargar la configuración del local.", error))
                    return@addSnapshotListener
                }
                try {
                    val configurations = snapshot?.documents.orEmpty().map { document ->
                        StoreProductConfigurationDocumentMapper.fromDocument(
                            document.id,
                            document.getData(ServerTimestampBehavior.ESTIMATE)
                                ?: error("Configuración de producto vacía."),
                        ).also { configuration ->
                            check(configuration.storeId == storeId && configuration.businessId == businessId) {
                                "La configuración no pertenece al local solicitado."
                            }
                        }
                    }
                    trySend(ApiResult.Success(configurations.sortedBy { it.productId }))
                } catch (mappingError: Exception) {
                    trySend(ApiResult.Failure("Se encontró una configuración de producto inválida.", mappingError))
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getConfiguration(
        storeId: String,
        productId: String,
    ): ApiResult<StoreProductConfiguration?> = try {
        require(storeId.isNotBlank() && productId.isNotBlank()) { "Selecciona un local y producto válidos." }
        val businessId = requireAuthorizedStore(storeId)
        val id = storeProductConfigurationId(storeId, productId)
        val document = configurationsRef.document(id).get(Source.SERVER).await()
        if (!document.exists()) {
            ApiResult.Success(null)
        } else {
            val configuration = StoreProductConfigurationDocumentMapper.fromDocument(
                document.id,
                document.data ?: error("Configuración de producto vacía."),
            )
            check(configuration.businessId == businessId && configuration.storeId == storeId && configuration.productId == productId) {
                "La configuración no corresponde al local y producto solicitados."
            }
            ApiResult.Success(configuration)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ApiResult.Failure(error.localizedMessage ?: "No se pudo consultar la configuración del producto.", error)
    }

    override suspend fun saveConfiguration(
        configuration: StoreProductConfiguration,
    ): ApiResult<StoreProductConfiguration> = try {
        require(configuration.isValid()) { "Revisa el local, producto y precio." }
        requireAuthorizedStore(configuration.storeId)
        val userId = access.requireUser().uid
        val reference = configurationsRef.document(configuration.id)

        firestore.runTransaction { transaction ->
            val store = transaction.get(storesRef.document(configuration.storeId))
            val product = transaction.get(productsRef.document(configuration.productId))
            requireConfigurationRelationships(
                configuration = configuration,
                storeBusinessId = store.getString("businessId"),
                productBusinessId = product.getString("businessId"),
            )
            transaction.set(
                reference,
                StoreProductConfigurationDocumentMapper.toFields(configuration, userId),
            )
        }.await()
        ApiResult.Success(configuration)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ApiResult.Failure(error.localizedMessage ?: "No se pudo guardar la configuración del producto.", error)
    }

    private suspend fun requireAuthorizedStore(storeId: String): String {
        access.requireStoreOperator(storeId)
        val store = storesRef.document(storeId).get(Source.SERVER).await()
        return checkNotNull(store.getString("businessId")) { "Local no disponible." }
    }
}
