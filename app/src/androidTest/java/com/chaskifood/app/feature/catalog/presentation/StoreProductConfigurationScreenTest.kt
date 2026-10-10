package com.chaskifood.app.feature.catalog.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreProductConfigurationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun precioDisponibilidadYBorradorSeConservanHastaGuardar() {
        val restoration = StateRestorationTester(compose)
        var savedPrice: Double? = null
        var savedAvailability: StoreProductAvailability? = null
        restoration.setContent {
            ChaskiTheme {
                ProductConfigurationCard(
                    storeId = "store-1",
                    item = StoreProductItem(
                        product = Product(
                            id = "product-1",
                            businessId = "business-1",
                            categoryId = "category-1",
                            categoryName = "Platos",
                            name = "Ají de gallina",
                            price = 10.0,
                        ),
                        configuration = null,
                    ),
                    operation = ProductConfigurationOperationState(),
                    onSave = { price, availability ->
                        savedPrice = price
                        savedAvailability = availability
                    },
                    onClearFeedback = {},
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("12,50")
        compose.onNodeWithContentDescription("Disponibilidad de Ají de gallina").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("12,50").assertIsDisplayed()
        compose.onNodeWithText("GUARDAR CONFIGURACIÓN").performClick()

        compose.runOnIdle {
            assertEquals(12.50, savedPrice!!, 0.0)
            assertEquals(StoreProductAvailability.AVAILABLE, savedAvailability)
        }
    }
}
