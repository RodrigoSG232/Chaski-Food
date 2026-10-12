package com.chaskifood.app.feature.search.data

import com.chaskifood.app.feature.address.domain.model.AddressCoordinates
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.DayOperatingHours
import com.chaskifood.app.feature.business.domain.StoreStatus
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.discover.domain.StoreDiscoveryCandidate
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseProductOfferReaderTest {
    private val address = AddressCoordinates(-12.0, -77.0)
    private val product = Product(id = "product", businessId = "business", categoryId = "category",
        name = "Plato", price = 99.0)
    private val category = ProductCategory(id = "category", businessId = "business", name = "Comida")
    private val configuration = StoreProductConfiguration("business", "store", "product", 10.50)

    @Test fun `local activo y categoria activa conservan el precio de la configuracion`() {
        val result = evaluateProductOffer(store(), product, category, configuration,
            address, DayOfWeekEnum.MONDAY, "12:00")
        assertEquals(ProductOfferReadResult.Found(product, configuration), result)
    }

    @Test fun `local inactivo o suspendido y categoria inactiva no producen oferta`() {
        for (status in listOf(StoreStatus.INACTIVE, StoreStatus.SUSPENDED)) {
            assertEquals(ProductOfferReadResult.Unavailable, evaluateProductOffer(
                store(status), product, category, configuration, address, DayOfWeekEnum.MONDAY, "12:00"))
        }
        assertEquals(ProductOfferReadResult.Unavailable, evaluateProductOffer(
            store(), product, category.copy(isActive = false), configuration,
            address, DayOfWeekEnum.MONDAY, "12:00"))
    }

    @Test fun `fuera de horario o cobertura no produce oferta`() {
        assertEquals(ProductOfferReadResult.Unavailable, evaluateProductOffer(
            store(), product, category, configuration, address, DayOfWeekEnum.MONDAY, "23:59"))
        assertEquals(ProductOfferReadResult.Unavailable, evaluateProductOffer(
            store(), product, category, configuration, AddressCoordinates(-13.0, -77.0),
            DayOfWeekEnum.MONDAY, "12:00"))
    }

    @Test fun `relaciones inconsistentes se rechazan y la configuracion ausente queda explicita`() {
        assertEquals(ProductOfferReadResult.InvalidData, evaluateProductOffer(
            store(), product.copy(businessId = "other"), category, configuration,
            address, DayOfWeekEnum.MONDAY, "12:00"))
        assertEquals(ProductOfferReadResult.InvalidData, evaluateProductOffer(
            store(), product, category.copy(businessId = "other"), configuration,
            address, DayOfWeekEnum.MONDAY, "12:00"))
        assertEquals(ProductOfferReadResult.InvalidData, evaluateProductOffer(
            store(), product, category, configuration.copy(storeId = "other"),
            address, DayOfWeekEnum.MONDAY, "12:00"))
        assertTrue(evaluateProductOffer(store(), product, category, null,
            address, DayOfWeekEnum.MONDAY, "12:00") is ProductOfferReadResult.Found)
    }

    private fun store(status: StoreStatus = StoreStatus.ACTIVE) = StoreDiscoveryCandidate(
        store = BusinessStore(id = "store", businessId = "business", name = "Local",
            latitude = -12.0, longitude = -77.0, status = status,
            operatingHours = DayOfWeekEnum.entries.map {
                DayOperatingHours(it, "08:00", "22:00")
            }),
        businessName = "Negocio", businessCategory = "Restaurante",
    )
}
