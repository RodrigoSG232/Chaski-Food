package com.chaskifood.app.feature.checkout.presentation

import com.chaskifood.app.feature.checkout.domain.CheckoutLine
import com.chaskifood.app.feature.checkout.domain.CheckoutQuote
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `un precio cambiado exige revision expresa y modalidad de pago`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        assertFalse(model.uiState.value.reviewComplete)
        model.acceptPriceChanges()
        assertTrue(model.uiState.value.reviewComplete)

        repository.next = CheckoutReviewResult.Blocked(listOf("Producto agotado"))
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
    }

    @Test fun `revalidar invalida la aceptacion anterior de precios`() = runTest {
        val repository = FakeCheckoutRepository(CheckoutReviewResult.Ready(quote(oldPrice = 1000)))
        val model = CheckoutViewModel(repository)
        model.review()
        runCurrent()
        model.selectPaymentMethod(CheckoutPaymentMethod.CASH_ON_DELIVERY)
        model.acceptPriceChanges()
        assertTrue(model.uiState.value.reviewComplete)

        repository.next = CheckoutReviewResult.Ready(quote(oldPrice = 900))
        model.review()
        runCurrent()
        assertFalse(model.uiState.value.reviewComplete)
        model.acceptPriceChanges()
        assertTrue(model.uiState.value.reviewComplete)
    }

    private fun quote(oldPrice: Long) = CheckoutQuote(
        ownerUid = "customer", storeId = "store", storeName = "Local",
        addressId = "address", addressLabel = "Casa", addressText = "Lima",
        lines = listOf(CheckoutLine("product", "Plato", 2, oldPrice, 1200, 2400)),
        subtotalMinor = 2400, deliveryFeeMinor = 500, totalMinor = 2900,
    )
}

private class FakeCheckoutRepository(var next: CheckoutReviewResult) : CheckoutRepository {
    override suspend fun review(): CheckoutReviewResult = next
}
