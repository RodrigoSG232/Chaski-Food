package com.chaskifood.app.feature.cart.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.database.ChaskiDatabase
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressBook
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.address.domain.model.AddressDraft
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.cart.data.local.CartLocalCoordinator
import com.chaskifood.app.feature.cart.data.mapper.toDomain
import com.chaskifood.app.feature.cart.domain.CartOperationResult
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultCartRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "cart-repository-${UUID.randomUUID()}.db"
    private lateinit var database: ChaskiDatabase
    private lateinit var sessions: TestSessions
    private lateinit var addresses: TestAddresses
    private lateinit var offers: TestOffers
    private lateinit var repository: DefaultCartRepository

    @Before fun setUp() {
        database = Room.databaseBuilder(context, ChaskiDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2).build()
        sessions = TestSessions()
        addresses = TestAddresses()
        offers = TestOffers()
        repository = DefaultCartRepository(sessions, addresses, offers,
            CartLocalCoordinator(database, database.cartDao(), sessions))
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun productoDisponibleSeGuardaConPrecioLocalExacto() = runBlocking {
        assertTrue(repository.addProduct("store-a", "product", 2) is CartOperationResult.Updated)
        val cart = (repository.observeCart().first { it is CartState.Ready } as CartState.Ready).cart
        assertEquals("a", cart.ownerUid)
        assertEquals(2, cart.items.single().quantity)
        assertEquals(1050L, cart.items.single().unitPriceMinor)
        assertEquals(2100L, cart.subtotalMinor)
    }

    @Test fun productosInactivosAgotadosYSinConfiguracionSeRechazan() = runBlocking {
        offers.product = offers.product.copy(isActive = false)
        assertTrue(repository.addProduct("store-a", "product", 1) is CartOperationResult.OfferRejected)
        offers.product = offers.product.copy(isActive = true)
        offers.configuration = offers.configuration.copy(availability = StoreProductAvailability.OUT_OF_STOCK)
        assertTrue(repository.addProduct("store-a", "product", 1) is CartOperationResult.OfferRejected)
        offers.configurationMissing = true
        assertTrue(repository.addProduct("store-a", "product", 1) is CartOperationResult.OfferRejected)
        assertEquals(null, database.cartDao().getCart("a"))
    }

    @Test fun cambioDeLocalDelMismoNegocioPideConfirmacionYPrecioNuevoOtraConfirmacion() = runBlocking {
        repository.addProduct("store-a", "product", 1)
        offers.configuration = offers.configuration.copy(storeId = "store-b")
        val first = repository.addProduct("store-b", "product", 1)
            as CartOperationResult.ConfirmationRequired
        assertFalse(first.offerChanged)
        assertEquals("store-a", cart().storeId)

        offers.configuration = offers.configuration.copy(price = 12.0)
        val renewed = repository.confirmStoreChange(first.token)
            as CartOperationResult.ConfirmationRequired
        assertTrue(renewed.offerChanged)
        assertEquals(1200L, renewed.incomingItem.unitPriceMinor)
        assertEquals(CartOperationResult.ConfirmationStale, repository.confirmStoreChange(first.token))
        assertEquals("store-a", cart().storeId)
        assertTrue(repository.confirmStoreChange(renewed.token) is CartOperationResult.Updated)
        assertEquals("store-b", cart().storeId)
        assertEquals(1200L, cart().subtotalMinor)
    }

    @Test fun cancelarYCambiarDireccionNoReemplazanElCarrito() = runBlocking {
        repository.addProduct("store-a", "product", 1)
        offers.configuration = offers.configuration.copy(storeId = "store-b")
        val cancelled = repository.addProduct("store-b", "product", 1)
            as CartOperationResult.ConfirmationRequired
        assertTrue(repository.cancelStoreChange(cancelled.token))
        assertEquals(CartOperationResult.ConfirmationStale, repository.confirmStoreChange(cancelled.token))

        val pending = repository.addProduct("store-b", "product", 1)
            as CartOperationResult.ConfirmationRequired
        addresses.select("address-b")
        assertEquals(CartOperationResult.AddressChanged, repository.confirmStoreChange(pending.token))
        assertEquals("store-a", cart().storeId)
    }

    @Test fun sesionCambiadaDuranteLaLecturaNoEscribeEnOtraCuenta() = runBlocking {
        offers.onRead = { sessions.change("b") }
        assertEquals(CartOperationResult.SessionChanged, repository.addProduct("store-a", "product", 1))
        assertEquals(null, database.cartDao().getCart("a"))
        assertEquals(null, database.cartDao().getCart("b"))
    }

    @Test fun redOCacheNoAutorizanAgregarPeroReducirYEliminarFuncionanSinConexion() = runBlocking {
        repository.addProduct("store-a", "product", 3)
        val successfulReads = offers.reads
        offers.result = ProductOfferReadResult.NetworkError
        assertEquals(CartOperationResult.OfferNetworkError, repository.addProduct("store-a", "product", 1))
        offers.result = ProductOfferReadResult.Unverified
        assertEquals(CartOperationResult.OfferUnverified, repository.addProduct("store-a", "product", 1))
        assertTrue(repository.changeQuantity("product", 2) is CartOperationResult.Updated)
        assertTrue(repository.removeProduct("product") is CartOperationResult.Updated)
        assertEquals(successfulReads + 2, offers.reads)
        assertTrue(cart().items.isEmpty())
    }

    @Test fun aumentoRevalidaOfertaYPersistenciaPorUid() = runBlocking {
        repository.addProduct("store-a", "product", 1)
        val readsBeforeIncrease = offers.reads
        assertTrue(repository.changeQuantity("product", 3) is CartOperationResult.Updated)
        assertEquals(readsBeforeIncrease + 1, offers.reads)
        assertEquals(3, database.cartDao().getCart("a")?.items?.single()?.quantity)
        sessions.change("b")
        addresses.owner("b")
        assertEquals(null, database.cartDao().getCart("b"))
        assertEquals(3, database.cartDao().getCart("a")?.items?.single()?.quantity)
    }

    private suspend fun cart() = requireNotNull(database.cartDao().getCart("a")).toDomain()
}

private class TestSessions : CartSessionSource {
    private val state = MutableStateFlow(CartSession("a", 0))
    override val sessions: StateFlow<CartSession> = state
    override fun current(): CartSession = state.value
    fun change(uid: String?) { state.value = CartSession(uid, state.value.generation + 1) }
}

private class TestAddresses : AddressRepository {
    private val first = Address("address-a", "Casa", "Lima", AddressCoordinates(-12.0, -77.0), "", "", 1, 1)
    private val second = first.copy(id = "address-b", updatedAtEpochMillis = 2)
    private val state = MutableStateFlow<AddressBookState>(AddressBookState.Ready(
        AddressBook("a", listOf(first, second), first.id, first.id)))
    override fun observeAddressBook(): Flow<AddressBookState> = state
    fun select(id: String) { state.value = AddressBookState.Ready(
        (state.value as AddressBookState.Ready).book.copy(selectedAddressId = id)) }
    fun owner(uid: String) { state.value = AddressBookState.Ready(AddressBook(uid, listOf(first), first.id, first.id)) }
    override suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address> = error("unused")
    override suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address> = error("unused")
    override suspend fun deleteAddress(addressId: String, replacementDefaultAddressId: String?): ApiResult<Unit> = error("unused")
    override suspend fun setDefaultAddress(addressId: String): ApiResult<Unit> = error("unused")
    override suspend fun selectAddress(addressId: String): ApiResult<Unit> = error("unused")
}

private class TestOffers : ProductOfferReader {
    var reads = 0
    var product = Product(id = "product", businessId = "business", categoryId = "category",
        name = "Producto", price = 99.0, isActive = true)
    var configuration = StoreProductConfiguration("business", "store-a", "product", 10.50)
    var configurationMissing = false
    var result: ProductOfferReadResult? = null
    var onRead: (() -> Unit)? = null
    override suspend fun read(storeId: String, productId: String,
        deliveryLocation: AddressCoordinates): ProductOfferReadResult {
        reads++
        onRead?.invoke()
        return result ?: ProductOfferReadResult.Found(product,
            if (configurationMissing) null else configuration)
    }
}
