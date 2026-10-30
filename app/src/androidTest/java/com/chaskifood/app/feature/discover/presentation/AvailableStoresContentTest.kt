package com.chaskifood.app.feature.discover.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.discover.domain.DiscoverableStore
import com.chaskifood.app.feature.discover.domain.SelectedDeliveryAddress
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailableStoresContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun muestraEstadoVacioComprensibleYPermiteCambiarDireccion() {
        var addressRequests = 0
        compose.setContent {
            ChaskiTheme {
                AvailableStoresContent(
                    state = AvailableStoresUiState.Empty(address()),
                    onSelectAddress = { addressRequests++ },
                    onRetry = {},
                    onStoreClick = {},
                )
            }
        }

        compose.onNodeWithText("No hay locales disponibles para esta dirección").assertIsDisplayed()
        compose.onNodeWithText("CAMBIAR DIRECCIÓN").performClick()
        compose.runOnIdle { assertEquals(1, addressRequests) }
    }

    @Test
    fun muestraInformacionDelLocalYPermiteElegirlo() {
        var selectedId: String? = null
        compose.setContent {
            ChaskiTheme {
                AvailableStoresContent(
                    state = AvailableStoresUiState.Success(address(), listOf(store())),
                    onSelectAddress = {},
                    onRetry = {},
                    onStoreClick = { selectedId = it.id },
                )
            }
        }

        compose.onNodeWithText("Local Miraflores").assertIsDisplayed().performClick()
        compose.onNodeWithText("Criolla · 1.2 km").assertIsDisplayed()
        compose.onNodeWithText("Av. Larco 123").assertIsDisplayed()
        compose.runOnIdle { assertEquals("store-1", selectedId) }
    }

    private fun address() = SelectedDeliveryAddress("address-1", "Casa", "Av. Principal 456")

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
}
