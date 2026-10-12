package com.chaskifood.app.feature.cart.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CartSessionTrackerTest {
    @Test
    fun cambioDeCuentaYLogoutLoginDelMismoUidIncrementanGeneracion() {
        val firstUser = Any()
        val secondLogin = Any()
        val tracker = CartSessionTracker("a", firstUser)
        val first = tracker.sessions.value

        assertEquals(first, tracker.update("a", firstUser))
        val signedOut = tracker.update(null, null)
        assertEquals(first.generation + 1, signedOut.generation)
        val sameUidAgain = tracker.update("a", secondLogin)
        assertEquals(first.generation + 2, sameUidAgain.generation)
        assertEquals("a", sameUidAgain.uid)
        assertEquals(first.generation + 3, tracker.update("b", Any()).generation)
    }

    @Test
    fun nuevaIdentidadConMismoUidInvalidaLaSesionAnterior() {
        val tracker = CartSessionTracker("a", Any())
        assertEquals(1, tracker.update("a", Any()).generation)
    }
}
