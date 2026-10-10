package com.chaskifood.app.core.navigation

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.business.domain.BusinessStore
import com.chaskifood.app.feature.business.presentation.StoreDialog
import com.chaskifood.app.feature.catalog.domain.Product
import com.chaskifood.app.feature.catalog.domain.ProductCategory
import com.chaskifood.app.feature.catalog.presentation.ProductDialog
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editedStoreDraftSurvivesSavedInstanceStateRestoration() {
        val restoration = StateRestorationTester(compose)
        lateinit var showDialog: (Boolean) -> Unit
        restoration.setContent {
            ChaskiTheme {
                var visible by rememberSaveable { mutableStateOf(true) }
                val drafts = rememberSaveableStateHolder()
                showDialog = { visible = it }
                if (visible) drafts.SaveableStateProvider("store") {
                    StoreDialog("business", BusinessStore(id = "store", name = "Local"), false, null,
                        onDismiss = {}, onSave = { _, _, _, _, _, _, _, _ -> })
                }
            }
        }
        compose.onNodeWithText("Nombre del Local *").performTextInput(" borrador")
        compose.onNodeWithText("Dirección *").performTextInput("Av. Lima 123")
        compose.runOnIdle { showDialog(false) } // Simulate loading/error hiding the dialog.
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { showDialog(true) }
        compose.onNodeWithText("Nombre del Local *").assertTextContains("Local borrador")
        compose.onNodeWithText("Dirección *").assertTextContains("Av. Lima 123")
    }

    @Test fun productDraftSurvivesSavedInstanceStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ChaskiTheme {
                ProductDialog("business", listOf(ProductCategory(id = "category", name = "Comida")),
                    Product(id = "product", name = "Pollo", price = 12.5),
                    onDismiss = {}, onSave = {}, saving = false, saveError = null)
            }
        }
        compose.onNodeWithText("Nombre del Producto *").performScrollTo().performTextInput(" al horno")
        compose.onNodeWithText("Descripción").performScrollTo().performTextInput("Con papas")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Nombre del Producto *").assertTextContains("Pollo al horno")
        compose.onNodeWithText("Descripción").assertTextContains("Con papas")
    }
}
