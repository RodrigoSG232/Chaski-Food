package com.chaskifood.app.feature.catalog.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreProductConfigurationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun productoContraidoConservaBorradorAlVolverAAbrir() {
        val item = StoreProductItem(Product(id = "product-1", businessId = "business-1",
            categoryId = "category-1", categoryName = "Platos", name = "Ají de gallina", price = 10.0), null)
        compose.setContent {
            var draft by remember { mutableStateOf(ProductConfigurationDraft("10.00", StoreProductAvailability.OUT_OF_STOCK)) }
            ChaskiTheme {
                ProductConfigurationCard("store-1", item, draft, dirty = true, invalid = false, saving = false,
                    onPriceChange = { draft = draft.copy(priceInput = it) },
                    onAvailabilityChange = { draft = draft.copy(availability = it) },
                    onPromotionChange = { draft = draft.copy(showInPromotions = it) })
            }
        }
        compose.onNodeWithContentDescription("Expandir Ají de gallina").performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("12,50")
        compose.onNodeWithContentDescription("Disponibilidad de Ají de gallina").performClick()
        compose.onNodeWithContentDescription("Contraer Ají de gallina").performClick()
        compose.onNodeWithContentDescription("Expandir Ají de gallina").performClick()
        compose.onNodeWithText("12,50").assertIsDisplayed()
        compose.onNodeWithText("Cambios sin guardar").assertIsDisplayed()
    }
}
