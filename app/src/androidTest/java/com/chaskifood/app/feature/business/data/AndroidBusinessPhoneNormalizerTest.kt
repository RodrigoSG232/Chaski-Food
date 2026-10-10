package com.chaskifood.app.feature.business.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBusinessPhoneNormalizerTest {
    private val normalizer = AndroidBusinessPhoneNormalizer()

    @Test fun normalizesNationalNumbersUsingSelectedCountry() {
        assertEquals("+51987654321", normalizer.toInternational("+51", "987654321"))
        assertEquals("+525512345678", normalizer.toInternational("+52", "5512345678"))
        assertEquals("+573123456789", normalizer.toInternational("+57", "3123456789"))
        assertEquals("+12025550123", normalizer.toInternational("+1", "2025550123"))
        assertEquals("+34612345678", normalizer.toInternational("+34", "612345678"))
    }
    @Test fun acceptsPeruvianLandlineContact() {
        assertEquals("+5114567890", normalizer.toInternational("+51", "14567890"))
    }
    @Test fun rejectsShortNumbersAndRepeatedPrefix() {
        assertNull(normalizer.toInternational("+51", "123"))
        assertNull(normalizer.toInternational("+51", "51987654321"))
    }
    @Test fun rejectsUnsupportedCountryAndNonNumericInput() {
        assertNull(normalizer.toInternational("+999", "987654321"))
        assertNull(normalizer.toInternational("+51", "9876AB321"))
        assertNull(normalizer.toInternational("+51", ""))
    }
}
