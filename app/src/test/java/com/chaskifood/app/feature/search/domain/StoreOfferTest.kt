package com.chaskifood.app.feature.search.domain

import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreOfferTest {

    @Test
    fun `oferta usa precio e imagen del producto configurado para el local`() {
        val offer = offer(
            products = listOf(product(price = 10.0, imageUrl = "https://storage.example/ceviche.jpg")),
            configurations = listOf(configuration(price = 18.50)),
        )

        val item = offer.products.single()
        assertEquals(18.50, item.price, 0.0)
        assertEquals("https://storage.example/ceviche.jpg", item.imageUrl)
        assertEquals("Ceviche clásico", item.description)
    }

    @Test
    fun `producto sin configuracion local no es comprable`() {
        assertTrue(offer(configurations = emptyList()).products.isEmpty())
    }

    @Test
    fun `excluye producto agotado o inactivo y categoria inactiva`() {
        val outOfStock = offer(configurations = listOf(configuration(
            availability = StoreProductAvailability.OUT_OF_STOCK,
        )))
        val inactiveProduct = offer(products = listOf(product(isActive = false)))
        val inactiveCategory = offer(categories = listOf(category(isActive = false)))

        assertTrue(outOfStock.products.isEmpty())
        assertTrue(inactiveProduct.products.isEmpty())
        assertTrue(inactiveCategory.products.isEmpty())
    }

    @Test
    fun `ignora configuraciones pertenecientes a otro local negocio o producto`() {
        val configurations = listOf(
            configuration(storeId = "store-2"),
            configuration(businessId = "business-2"),
            configuration(productId = "product-2"),
        )

        assertTrue(offer(configurations = configurations).products.isEmpty())
    }

    @Test
    fun `solo expone categorias que contienen productos comprables`() {
        val offer = offer(categories = listOf(
            category(id = "category-1", name = "Pescados", order = 2),
            category(id = "category-empty", name = "Postres", order = 1),
        ))

        assertEquals(listOf("Pescados"), offer.categories.map { it.name })
    }

    @Test
    fun `busca productos sin distinguir mayusculas ni tildes`() {
        val results = searchStoreOffers(listOf(offer()), "  CÉVICHE  ")

        assertEquals("product-1", results.products.single().id)
        assertEquals("store-1", results.stores.single().id)
    }

    @Test
    fun `busca establecimientos por nombre categoria o direccion`() {
        val offers = listOf(offer())

        assertEquals("store-1", searchStoreOffers(offers, "sabor peruano").stores.single().id)
        assertEquals("store-1", searchStoreOffers(offers, "criolla").stores.single().id)
        assertEquals("store-1", searchStoreOffers(offers, "larco").stores.single().id)
    }

    @Test
    fun `filtro de categoria conserva solo oferta correspondiente`() {
        val secondProduct = product(id = "product-2", categoryId = "category-2", name = "Suspiro")
        val offer = offer(
            categories = listOf(category(), category(id = "category-2", name = "Postres")),
            products = listOf(product(), secondProduct),
            configurations = listOf(configuration(), configuration(productId = "product-2")),
        )

        val results = searchStoreOffers(listOf(offer), query = "", categoryId = "category-2")

        assertEquals(listOf("Suspiro"), results.products.map { it.name })
        assertEquals(listOf("store-1"), results.stores.map { it.id })
    }

    private fun offer(
        categories: List<ProductCategory> = listOf(category()),
        products: List<Product> = listOf(product()),
        configurations: List<StoreProductConfiguration> = listOf(configuration()),
    ) = buildStoreOffer(store(), categories, products, configurations)

    private fun store() = DiscoverableStore(
        id = "store-1",
        businessId = "business-1",
        businessName = "Sabor Peruano",
        businessCategory = "Criolla",
        name = "Local Miraflores",
        address = "Av. Larco 123",
        phone = "999888777",
        distanceKm = 1.2,
    )

    private fun category(
        id: String = "category-1",
        name: String = "Pescados",
        order: Int = 0,
        isActive: Boolean = true,
    ) = ProductCategory(id, "business-1", name, order, isActive)

    private fun product(
        id: String = "product-1",
        categoryId: String = "category-1",
        name: String = "Ceviche",
        price: Double = 10.0,
        imageUrl: String? = null,
        isActive: Boolean = true,
    ) = Product(
        id = id,
        businessId = "business-1",
        categoryId = categoryId,
        name = name,
        description = "Ceviche clásico",
        price = price,
        imageUrl = imageUrl,
        isActive = isActive,
    )

    private fun configuration(
        businessId: String = "business-1",
        storeId: String = "store-1",
        productId: String = "product-1",
        price: Double = 18.50,
        availability: StoreProductAvailability = StoreProductAvailability.AVAILABLE,
    ) = StoreProductConfiguration(businessId, storeId, productId, price, availability)
}
