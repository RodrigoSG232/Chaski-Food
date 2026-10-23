package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class CheckoutLineRequest(
    val productId: String, val quantity: Int, val expectedPriceMinor: Long,
)

@Serializable data class CheckoutRequest(
    val storeId: String, val addressId: String, val paymentMethod: String,
    val items: List<CheckoutLineRequest>, val requestId: String? = null,
)

@Serializable data class CheckoutLine(
    val productId: String, val name: String, val quantity: Int,
    val unitPriceMinor: Long, val lineTotalMinor: Long, val priceChanged: Boolean = false,
)

@Serializable data class AddressSnapshot(
    val addressText: String, val label: String, val reference: String,
    val instructions: String, val lat: Double, val lng: Double,
)

@Serializable data class CheckoutQuote(
    val storeId: String, val storeName: String, val businessId: String,
    val address: AddressSnapshot, val items: List<CheckoutLine>,
    val subtotalMinor: Long, val deliveryFeeMinor: Long, val totalMinor: Long,
    val paymentMethod: String, val priceChanged: Boolean,
)

@Serializable data class CreatedOrder(val orderId: String, val status: String)
@Serializable data class StoreOrder(
    val orderId: String, val status: String, val createdAtMillis: Long,
    val items: List<CheckoutLine>, val address: AddressSnapshot?,
    val subtotalMinor: Long, val deliveryFeeMinor: Long, val totalMinor: Long,
    val paymentMethod: String,
)
@Serializable data class StoreOrdersResponse(val orders: List<StoreOrder>)
@Serializable private data class CheckoutProblem(
    val code: String = "", val message: String = "No se pudo validar la compra.",
    val details: List<CheckoutItemProblem> = emptyList(),
)
@Serializable private data class CheckoutItemProblem(val productId: String, val issue: String)

interface CheckoutApi {
    @POST("quote") suspend fun quote(@Header("Authorization") bearer: String, @Body request: CheckoutRequest): CheckoutQuote
    @POST("orders") suspend fun create(@Header("Authorization") bearer: String, @Body request: CheckoutRequest): CreatedOrder

    @GET("store-orders") suspend fun storeOrders(
        @Header("Authorization") bearer: String, @Query("storeId") storeId: String,
    ): StoreOrdersResponse
}

class CheckoutRemote @Inject constructor(
    private val auth: FirebaseAuth, private val api: CheckoutApi, private val json: Json,
) {
    private suspend fun bearer(): String {
        val user = auth.currentUser ?: error("Inicia sesión para comprar.")
        return "Bearer " + checkNotNull(user.getIdToken(true).await().token)
    }
    suspend fun quote(request: CheckoutRequest): CheckoutQuote = api.quote(bearer(), request)
    suspend fun create(request: CheckoutRequest): CreatedOrder = api.create(bearer(), request)
    suspend fun storeOrders(storeId: String): List<StoreOrder> = api.storeOrders(bearer(), storeId).orders
    fun message(error: Throwable): String {
        if (error is HttpException) {
            val raw = error.response()?.errorBody()?.string()
            val problem = runCatching { json.decodeFromString<CheckoutProblem>(raw.orEmpty()) }.getOrNull()
            if (problem != null) {
                val products = problem.details.map { it.productId }.take(3)
                return problem.message + if (products.isEmpty()) "" else " Productos: ${products.joinToString()}."
            }
        }
        return error.message ?: "No se pudo conectar. Reintenta."
    }
}

@Module @InstallIn(SingletonComponent::class)
object CheckoutNetworkModule {
    @Provides @Singleton fun provideCheckoutApi(client: OkHttpClient, json: Json): CheckoutApi =
        Retrofit.Builder().baseUrl(BuildConfig.CHECKOUT_API_URL).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(CheckoutApi::class.java)
}
