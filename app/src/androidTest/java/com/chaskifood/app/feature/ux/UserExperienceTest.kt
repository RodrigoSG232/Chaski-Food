package com.chaskifood.app.feature.ux

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaskifood.app.feature.auth.presentation.AuthPasswordVisibilityButton
import com.chaskifood.app.feature.auth.presentation.AuthTextField
import com.chaskifood.app.feature.auth.presentation.SocialAuthRow
import com.chaskifood.app.feature.business.domain.DayOfWeekEnum
import com.chaskifood.app.feature.business.domain.defaultWeeklyOperatingHours
import com.chaskifood.app.feature.business.presentation.HoursEditor
import com.chaskifood.app.feature.checkout.presentation.AddCardScreen
import com.chaskifood.app.feature.search.presentation.SearchFoodScreen
import com.chaskifood.app.ui.theme.ChaskiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an emulator/test device; these journeys never call Firebase or submit payments. */
@RunWith(AndroidJUnit4::class)
class UserExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun passwordVisibilityHasNamedActionAndKeepsTypedValue() {
        val restoration = StateRestorationTester(compose)
        var password = ""
        restoration.setContent {
            ChaskiTheme {
                var visible by rememberSaveable { mutableStateOf(false) }
                var value by rememberSaveable { mutableStateOf("") }
                password = value
                AuthTextField(value, { value = it }, label = "Contraseña",
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { AuthPasswordVisibilityButton(visible) { visible = !visible } })
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("MiClave123")
        compose.onNodeWithContentDescription("Mostrar contraseña")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Ocultar contraseña").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("MiClave123", password) }
    }

    @Test fun removingOneRecentSearchKeepsOtherTermsAndDoesNotOpenResults() {
        compose.setContent { ChaskiTheme { SearchFoodScreen({}, {}, {}) } }
        compose.onNodeWithContentDescription("Eliminar búsqueda: Papas con chile y queso").performClick()
        compose.onNodeWithText("Papas con chile y queso").assertDoesNotExist()
        compose.onNodeWithText("Injera con Doro Wat").assertIsDisplayed()
    }

    @Test fun unavailableFacebookIsAbsentButGoogleStillWorks() {
        var googleClicks = 0
        compose.setContent { ChaskiTheme { SocialAuthRow(onGoogle = { googleClicks++ }) } }
        compose.onNodeWithText("Facebook").assertDoesNotExist()
        compose.onNodeWithText("Google").performClick()
        compose.runOnIdle { assertEquals(1, googleClicks) }
    }

    @Test fun pendingCardScreenDoesNotAskForPaymentData() {
        compose.setContent { ChaskiTheme { AddCardScreen(onBack = {}, onTabSelected = {}) } }
        compose.onNodeWithText("Próximamente").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("AGREGAR TARJETA").assertDoesNotExist()
    }

    @Test fun hoursExplainSundayRolloverAndCancelLeavesDraftUntouched() {
        val hours = defaultWeeklyOperatingHours().map {
            if (it.dayOfWeek == DayOfWeekEnum.SUNDAY) it.copy(openTime = "23:00", closeTime = "03:00") else it
        }
        var changed = false
        compose.setContent {
            ChaskiTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoursEditor(hours, onChange = { changed = true }, onSave = {}, enabled = true, operationState = null)
                }
            }
        }
        compose.onNodeWithText("Cierra el Lunes a las 03:00 (día siguiente).").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Cierre del Domingo: 03:00").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertFalse(changed) }
    }
}
