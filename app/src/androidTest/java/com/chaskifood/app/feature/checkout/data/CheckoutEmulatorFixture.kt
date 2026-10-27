package com.chaskifood.app.feature.checkout.data

import android.content.Context
import android.os.SystemClock
import com.chaskifood.app.feature.checkout.data.remote.StoreEligibilityApi
import com.chaskifood.app.feature.checkout.data.remote.DeliveryQuoteApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.feature.address.data.local.AddressSelectionStore
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressBook
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.address.domain.model.AddressDraft
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.data.DefaultCartRepository
import com.chaskifood.app.feature.cart.data.FirebaseCartSessionSource
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.data.mapper.toEntity
import com.chaskifood.app.feature.cart.data.mapper.toItemEntities
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.DeliveryQuote
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteReader
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteResult
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.search.data.FirebaseProductOfferReader
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Un FirebaseApp nombrado impide que estas pruebas utilicen el proyecto configurado para la app. */
internal class CheckoutEmulatorFixture {
    private val androidContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = requireNotNull(FirebaseApp.initializeApp(androidContext, FirebaseOptions.Builder()
        .setProjectId(PROJECT_ID)
        .setApplicationId("1:123456789:android:hu15checkout")
        .setApiKey("demo-api-key")
        .build(), "hu15-checkout-emulator"))
    val auth: FirebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(app).apply {
        useEmulator(HOST, FIRESTORE_PORT)
        firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
    }
    val sessions = FirebaseCartSessionSource(auth)
    val addresses = TestCheckoutAddresses()
    val selection = TestCheckoutSelection()
    val realOffers = FirebaseProductOfferReader(firestore)
    lateinit var database: ChaskiDatabase
        private set
    lateinit var repository: FirebaseCheckoutRepository
        private set
    private lateinit var local: CartLocalCoordinator
    var uid: String = ""
        private set
    private var storeFields = emptyMap<String, Any>()
    private var configurationFields = emptyMap<String, Any>()
    private var emailCredentials: Pair<String, String>? = null
    @Volatile var afterHttpResponse: (() -> Unit)? = null
    var lastHttpMs = 0L
        private set
    var lastBackendMs = 0.0
        private set
    var lastBackendReads = 0
        private set
    var deliveryResult: DeliveryQuoteResult? = null
    var beforeDeliveryReturn: (suspend () -> Unit)? = null
    @Volatile var afterQuoteHttpResponse: (() -> Unit)? = null
    @Volatile var quoteHttpCalls = 0
        private set
    @Volatile var quoteHttpStatus = 0
        private set
    @Volatile var quoteRouteSource: String? = null
        private set
    @Volatile var quoteBackendReads = 0
        private set

    init {
        check(FirebaseApp.getApps(androidContext).all { it.options.projectId == PROJECT_ID }) {
            "Las pruebas HU15 solo admiten el proyecto Firebase ficticio."
        }
    }

    suspend fun beginTest() {
        afterHttpResponse = null
        deliveryResult = null
        beforeDeliveryReturn = null
        afterQuoteHttpResponse = null
        quoteHttpCalls = 0
        quoteHttpStatus = 0
        quoteRouteSource = null
        quoteBackendReads = 0
        auth.signOut()
        clearAccounts()
        clearDocuments()
        uid = requireNotNull(auth.signInAnonymously().await().user).uid
        createLocalCheckout(realOffers)
    }

    suspend fun useEmailAccount() {
        auth.signOut()
        val email = "buyer-${UUID.randomUUID()}@example.invalid"
        val password = "emulator-only-password"
        uid = requireNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid
        emailCredentials = email to password
        createLocalCheckout(realOffers)
    }

    suspend fun signInSameAccount() {
        val (email, password) = requireNotNull(emailCredentials)
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun createLocalCheckout(offers: ProductOfferReader, useRealDeliveryQuotes: Boolean = false) {
        if (::database.isInitialized) database.close()
        database = Room.inMemoryDatabaseBuilder(androidContext, ChaskiDatabase::class.java).build()
        local = CartLocalCoordinator(database, database.cartDao(), sessions)
        val carts = DefaultCartRepository(sessions, addresses, offers, local)
        val contexts = CheckoutContextSource(sessions, carts, addresses,
            StoredCheckoutLocalSnapshotReader(local, selection))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val start = SystemClock.elapsedRealtimeNanos()
            val response = chain.proceed(chain.request())
            if (chain.request().url.encodedPath.endsWith("/delivery-quotes")) {
                quoteHttpCalls++
                quoteHttpStatus = response.code
                quoteRouteSource = response.header("X-Route-Source")
                quoteBackendReads = response.header("X-Quote-Firestore-Reads")?.toIntOrNull() ?: 0
                afterQuoteHttpResponse?.invoke()
            } else {
                lastHttpMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                lastBackendMs = response.header("Server-Timing")?.substringAfter("dur=")?.toDoubleOrNull() ?: 0.0
                lastBackendReads = response.header("X-Eligibility-Reads")?.toIntOrNull() ?: 0
                afterHttpResponse?.invoke()
            }
            response
        }.build()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder().client(client)
            .baseUrl("http://$HOST:5001/$PROJECT_ID/southamerica-west1/foodApi/api/v1/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val tokens = FirebaseBuyerTokenSource(auth, sessions)
        val eligibility = HttpStorePurchaseEligibilityReader(retrofit.create(StoreEligibilityApi::class.java), tokens)
        val simulatedQuotes = object : DeliveryQuoteReader {
            override suspend fun quote(storeId: String, addressId: String, ownerUid: String,
                                       sessionGeneration: Long): DeliveryQuoteResult {
                beforeDeliveryReturn?.invoke()
                deliveryResult?.let { return it }
                val issued = System.currentTimeMillis()
                val selected = (addresses.observeAddressBook().first() as AddressBookState.Ready)
                    .book.addresses.single { it.id == addressId }
                return DeliveryQuoteResult.Quoted(DeliveryQuote(1_918.8, 380, CartCurrency.PEN,
                    "fixture-tariff", issued, issued + 300_000L,
                    AddressCoordinates((storeFields.getValue("latitude") as Number).toDouble(),
                        (storeFields.getValue("longitude") as Number).toDouble()), selected.location))
            }
        }
        val quotes = if (useRealDeliveryQuotes) HttpDeliveryQuoteReader(
            retrofit.create(DeliveryQuoteApi::class.java), tokens, json) else simulatedQuotes
        repository = FirebaseCheckoutRepository(contexts, offers, firestore, eligibility,
            quotes, SystemCheckoutClock())
    }

    /** Solo el caso E2E necesita la copia Firestore que el backend lee bajo el UID del comprador. */
    fun seedRemoteAddress(address: Address, ownerUid: String = uid) {
        put("users/$ownerUid/addresses/${address.id}", mapOf("location" to mapOf(
            "lat" to address.location.latitude, "lng" to address.location.longitude)))
    }

    suspend fun seed(products: Int = 1, categories: Int = 1,
                     address: Address? = defaultAddress()): CheckoutContext {
        require(products in 1..10)
        require(categories in 1..products)
        put("business_requests/$BUSINESS_ID", mapOf(
            "ownerUid" to "fixture-owner", "status" to "APPROVED", "businessName" to "Negocio de prueba"))
        storeFields = mapOf(
            "businessId" to BUSINESS_ID, "name" to "Local de prueba", "address" to "Dirección de prueba",
            "latitude" to -12.0, "longitude" to -77.0, "deliveryRadiusKm" to 5.0,
            "status" to "ACTIVE", "operationalStatus" to "OPEN",
            "operatingHours" to listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "FRIDAY", "SATURDAY", "SUNDAY").map { day ->
                mapOf("dayOfWeek" to day, "openTime" to "00:00", "closeTime" to "23:59", "enabled" to true)
            },
        )
        put("stores/$STORE_ID", storeFields)
        for (categoryIndex in 1..categories) {
            put("categories/${categoryId(categoryIndex)}", mapOf(
                "businessId" to BUSINESS_ID, "name" to "Categoría", "isActive" to true))
        }
        for (index in 1..products) {
            val id = productId(index)
            put("products/$id", mapOf(
                "businessId" to BUSINESS_ID,
                "categoryId" to categoryId((index - 1) % categories + 1), "name" to "Producto $index",
                "price" to 99.0, "prepTimeMinutes" to 15, "isActive" to true))
            configurationFields = mapOf(
                "businessId" to BUSINESS_ID, "storeId" to STORE_ID, "productId" to id,
                "price" to 10.5, "availability" to "AVAILABLE",
                "updatedAt" to TimestampValue, "updatedBy" to "fixture-owner")
            put("store_product_configs/${STORE_ID}_$id", configurationFields)
        }
        val cart = Cart(uid, (1..products).map { index ->
            CartProductSnapshot(BUSINESS_ID, STORE_ID, productId(index), "Producto $index", 1000L, 1)
        })
        database.cartDao().saveCart(cart.toEntity(), cart.toItemEntities())
        addresses.set(uid, address)
        selection.select(uid, address?.id)
        return readyContext()
    }

    suspend fun readyContext(): CheckoutContext = withTimeout(20_000) {
        repository.observeContext().filterIsInstance<CheckoutContextState.Ready>().first().context
    }

    fun updateStore(status: String? = null, operationalStatus: String? = null,
                    radiusKm: Double? = null) {
        storeFields = storeFields + listOfNotNull(
            status?.let { "status" to it }, operationalStatus?.let { "operationalStatus" to it },
            radiusKm?.let { "deliveryRadiusKm" to it }).toMap()
        put("stores/$STORE_ID", storeFields)
    }

    fun updateStoreLocation(latitude: Double, longitude: Double) {
        storeFields = storeFields + mapOf("latitude" to latitude, "longitude" to longitude)
        put("stores/$STORE_ID", storeFields)
    }

    fun updateBusiness(status: String) {
        put("business_requests/$BUSINESS_ID", mapOf(
            "ownerUid" to "fixture-owner", "status" to status, "businessName" to "Negocio de prueba"))
    }

    fun updateConfiguration(price: Double? = null, availability: String? = null) {
        configurationFields = configurationFields + listOfNotNull(
            price?.let { "price" to it }, availability?.let { "availability" to it }).toMap()
        put("store_product_configs/${STORE_ID}_${productId(1)}", configurationFields)
    }

    fun updateCategory(index: Int = 1, isActive: Boolean) {
        put("categories/${categoryId(index)}", mapOf(
            "businessId" to BUSINESS_ID, "name" to "Categoría", "isActive" to isActive))
    }

    fun delete(path: String) { request("DELETE", "/v1/projects/$PROJECT_ID/databases/(default)/documents/$path") }
    fun collection(path: String): JSONObject = JSONObject(request("GET",
        "/v1/projects/$PROJECT_ID/databases/(default)/documents/$path"))

    fun close() {
        if (::database.isInitialized) database.close()
        auth.signOut()
        firestore.terminate()
        app.delete()
    }

    private fun put(path: String, fields: Map<String, Any>) {
        val body = JSONObject().put("fields", fieldMap(fields))
        request("PATCH", "/v1/projects/$PROJECT_ID/databases/(default)/documents/$path", body)
    }

    private fun clearDocuments() {
        request("DELETE", "/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents")
    }

    private fun clearAccounts() {
        request("DELETE", "/emulator/v1/projects/$PROJECT_ID/accounts", port = AUTH_PORT)
    }

    private fun request(method: String, path: String, body: JSONObject? = null,
                        port: Int = FIRESTORE_PORT): String {
        val connection = URL("http://$HOST:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Emulator REST $method $path devolvió HTTP $code: $response" }
            return response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val PROJECT_ID = "demo-chaski-hu15"
        val HOST: String
            get() = InstrumentationRegistry.getArguments().getString("hu15Host", "10.0.2.2")
                .also { require(it == "10.0.2.2" || it == "127.0.0.1") }
        const val FIRESTORE_PORT = 8089
        const val AUTH_PORT = 9099
        const val BUSINESS_ID = "fixture-business"
        const val STORE_ID = "fixture-store"
        const val CATEGORY_ID = "fixture-category"
        fun productId(index: Int) = "fixture-product-$index"
        fun categoryId(index: Int) = if (index == 1) CATEGORY_ID else "$CATEGORY_ID-$index"
        fun defaultAddress(id: String = "fixture-address", location: AddressCoordinates =
            AddressCoordinates(-12.0, -77.0)) = Address(id, "Prueba", "Dirección de prueba",
            location, "", "", 1L, 1L)

        private object TimestampValue

        private fun fieldMap(fields: Map<String, Any>): JSONObject = JSONObject().apply {
            fields.forEach { (name, value) -> put(name, firestoreValue(value)) }
        }

        private fun firestoreValue(value: Any): JSONObject = when (value) {
            is String -> JSONObject().put("stringValue", value)
            is Boolean -> JSONObject().put("booleanValue", value)
            is Int, is Long -> JSONObject().put("integerValue", value.toString())
            is Double -> JSONObject().put("doubleValue", value)
            is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray().apply {
                value.forEach { element -> put(firestoreValue(requireNotNull(element))) }
            }))
            is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", fieldMap(
                value.entries.associate { (key, element) -> key as String to requireNotNull(element) })))
            TimestampValue -> JSONObject().put("timestampValue", "2026-01-01T00:00:00Z")
            else -> error("Tipo de fixture no soportado")
        }
    }
}

internal class TestCheckoutAddresses : AddressRepository {
    private val state = MutableStateFlow<AddressBookState>(AddressBookState.SignedOut)
    override fun observeAddressBook(): Flow<AddressBookState> = state
    fun set(uid: String, address: Address?) {
        state.value = AddressBookState.Ready(AddressBook(uid, listOfNotNull(address),
            address?.id, address?.id))
    }
    override suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address> = error("unused")
    override suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address> = error("unused")
    override suspend fun deleteAddress(addressId: String, replacementDefaultAddressId: String?): ApiResult<Unit> = error("unused")
    override suspend fun setDefaultAddress(addressId: String): ApiResult<Unit> = error("unused")
    override suspend fun selectAddress(addressId: String): ApiResult<Unit> = error("unused")
}

internal class TestCheckoutSelection : AddressSelectionStore {
    private val selected = mutableMapOf<String, MutableStateFlow<String?>>()
    private fun state(uid: String) = selected.getOrPut(uid) { MutableStateFlow(null) }
    override fun observe(ownerUid: String): Flow<String?> = state(ownerUid)
    override suspend fun select(ownerUid: String, addressId: String?) { state(ownerUid).value = addressId }
    override suspend fun reconcile(ownerUid: String, availableAddressIds: Set<String>,
                                   defaultAddressId: String?): String? {
        val current = state(ownerUid).value
        val resolved = current?.takeIf { it in availableAddressIds }
            ?: defaultAddressId?.takeIf { it in availableAddressIds }
        state(ownerUid).value = resolved
        return resolved
    }
}
