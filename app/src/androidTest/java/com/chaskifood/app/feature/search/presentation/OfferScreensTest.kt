package com.chaskifood.app.feature.search.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.search.domain.OfferCategory
import com.chaskifood.app.feature.search.domain.OfferSearchResults
import com.chaskifood.app.feature.search.domain.StoreOffer
import com.chaskifood.app.feature.search.domain.StoreOfferProduct
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun busquedaMuestraLocalesYProductosComprables() {
        var selectedStore: String? = null
        var selectedProduct: String? = null
        val product = product()
        compose.setContent {
            ChaskiTheme {
                OfferSearchContent(
                    state = OfferSearchUiState.Content(
                        query = "",
                        selectedCategoryId = null,
                        categories = listOf(category()),
                        results = OfferSearchResults(listOf(store()), listOf(product)),
                    ),
                    onQueryChange = {},
                    onCategorySelected = {},
                    onStoreClick = { selectedStore = it.id },
                    onProductClick = { selectedProduct = it.id },
                    onSelectAddress = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Local Miraflores").assertIsDisplayed().performClick()
        compose.onNodeWithText("Ají de gallina").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("store-1", selectedStore)
            assertEquals("product-1", selectedProduct)
        }
    }

    @Test
    fun ofertaDelLocalFiltraProductosPorCategoria() {
        val main = product()
        val dessert = product().copy(
            id = "product-2",
            categoryId = "category-2",
            categoryName = "Postres",
            name = "Suspiro limeño",
        )
        compose.setContent {
            ChaskiTheme {
                StoreOfferContent(
                    offer = StoreOffer(
                        store = store(),
                        categories = listOf(category(), OfferCategory("category-2", "Postres", 2)),
                        products = listOf(main, dessert),
                    ),
                    onProductClick = {},
                )
            }
        }

        compose.onNodeWithText("Postres").performClick()
        compose.onNodeWithText("Suspiro limeño").assertIsDisplayed()
        compose.onNodeWithText("Ají de gallina").assertIsNotDisplayed()
    }

    private fun category() = OfferCategory("category-1", "Criollos", 1)

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

    private fun product() = StoreOfferProduct(
        id = "product-1",
        businessId = "business-1",
        storeId = "store-1",
        storeName = "Local Miraflores",
        categoryId = "category-1",
        categoryName = "Criollos",
        name = "Ají de gallina",
        description = "Clásico plato peruano",
        price = 18.9,
        imageUrl = null,
        prepTimeMinutes = 20,
    )
}
