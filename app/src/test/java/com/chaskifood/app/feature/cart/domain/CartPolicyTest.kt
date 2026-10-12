package com.chaskifood.app.feature.cart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CartPolicyTest {
    private val policy = CartPolicy()
    private val empty = Cart(ownerUid = "uid-1")

    @Test
    fun `primer producto fija local y calcula subtotal provisional en PEN`() {
        val updated = policy.add(empty, item(price = 1250, quantity = 2)) as CartChangeResult.Updated

        assertTrue(empty.items.isEmpty())
        assertEquals(0L, empty.subtotalMinor)
        assertEquals("uid-1", updated.cart.ownerUid)
        assertEquals("business-1", updated.cart.businessId)
        assertEquals("store-1", updated.cart.storeId)
        assertEquals(CartCurrency.PEN, updated.cart.currency)
        assertEquals(2500L, updated.cart.subtotalMinor)
    }

    @Test
    fun `producto repetido acumula cantidad y adopta la referencia de precio mas reciente`() {
        val original = cart(item(price = 1250, quantity = 2))

        val updated = policy.add(original, item(price = 1300, quantity = 3)) as CartChangeResult.Updated

        assertEquals(2, original.items.single().quantity)
        assertEquals(1250L, original.items.single().unitPriceMinor)
        assertEquals(1, updated.cart.items.size)
        assertEquals(5, updated.cart.items.single().quantity)
        assertEquals(1300L, updated.cart.items.single().unitPriceMinor)
        assertEquals(6500L, updated.cart.subtotalMinor)
    }

    @Test
    fun `productos distintos del mismo local suman importes exactos`() {
        val original = cart(item(price = 199, quantity = 3))

        val updated = policy.add(original, item(productId = "product-2", price = 89, quantity = 2))
            as CartChangeResult.Updated

        assertEquals(2, updated.cart.items.size)
        assertEquals(775L, updated.cart.subtotalMinor)
        assertEquals("store-1", updated.cart.storeId)
    }

    @Test
    fun `otro local incluso del mismo negocio requiere confirmacion sin modificar carrito`() {
        val original = cart(item())
        val candidate = item(storeId = "store-2")

        val result = policy.add(original, candidate) as CartChangeResult.RequiresConfirmation

        assertSame(original, result.currentCart)
        assertSame(candidate, result.incomingItem)
        assertEquals("store-1", original.storeId)
        assertEquals(1250L, original.subtotalMinor)
        assertEquals(listOf("product-1"), original.items.map { it.productId })
    }

    @Test
    fun `inconsistencia de negocio dentro del mismo local se rechaza`() {
        val result = policy.add(cart(item()), item(businessId = "business-2"))

        assertEquals(
            CartChangeResult.Rejected(CartChangeRejectionReason.INCONSISTENT_BUSINESS),
            result,
        )
    }

    @Test
    fun `cantidad cero o negativa no elimina implicitamente una linea`() {
        val original = cart(item())

        for (quantity in listOf(0, -1)) {
            assertEquals(
                CartChangeResult.Rejected(CartChangeRejectionReason.INVALID_QUANTITY),
                policy.changeQuantity(original, "product-1", quantity),
            )
        }
        assertEquals(1, original.items.single().quantity)
    }

    @Test
    fun `la acumulacion rechaza desbordamiento de cantidad`() {
        val original = cart(item(price = 1, quantity = Int.MAX_VALUE))

        assertEquals(
            CartChangeResult.Rejected(CartChangeRejectionReason.QUANTITY_OVERFLOW),
            policy.add(original, item(price = 1)),
        )
        assertEquals(Int.MAX_VALUE, original.items.single().quantity)
    }

    @Test
    fun `subtotal rechaza desbordamiento al multiplicar o sumar`() {
        val multiplication = policy.add(empty, item(price = Long.MAX_VALUE, quantity = 2))
        val addition = policy.add(
            cart(item(price = Long.MAX_VALUE)),
            item(productId = "product-2", price = 1),
        )

        assertEquals(CartChangeResult.Rejected(CartChangeRejectionReason.AMOUNT_OVERFLOW), multiplication)
        assertEquals(CartChangeResult.Rejected(CartChangeRejectionReason.AMOUNT_OVERFLOW), addition)
    }

    @Test
    fun `eliminar una linea conserva las demas y actualiza subtotal`() {
        val original = cart(item(), item(productId = "product-2", price = 200))

        val updated = policy.remove(original, "product-1") as CartChangeResult.Updated

        assertEquals(listOf("product-2"), updated.cart.items.map { it.productId })
        assertEquals(200L, updated.cart.subtotalMinor)
        assertEquals(2, original.items.size)
    }

    @Test
    fun `eliminar ultimo producto libera local y deja subtotal cero`() {
        val updated = policy.remove(cart(item()), "product-1") as CartChangeResult.Updated

        assertTrue(updated.cart.items.isEmpty())
        assertEquals(null, updated.cart.storeId)
        assertEquals(null, updated.cart.businessId)
        assertEquals(0L, updated.cart.subtotalMinor)
        assertEquals("uid-1", updated.cart.ownerUid)
    }

    @Test
    fun `cambiar cantidad conserva precio provisional y recalcula subtotal`() {
        val original = cart(item(price = 1299, quantity = 2))

        val updated = policy.changeQuantity(original, "product-1", 3) as CartChangeResult.Updated

        assertEquals(1299L, updated.cart.items.single().unitPriceMinor)
        assertEquals(3897L, updated.cart.subtotalMinor)
        assertEquals(2598L, original.subtotalMinor)
    }

    @Test
    fun `el carrito conserva su contenido aunque cambie la lista usada para crearlo`() {
        val source = mutableListOf(item())
        val original = Cart("uid-1", source)

        source.clear()

        assertEquals(1, original.items.size)
        assertEquals(1250L, original.subtotalMinor)
        assertEquals("store-1", original.storeId)
    }

    private fun cart(vararg items: CartProductSnapshot) = Cart("uid-1", items.toList())

    private fun item(
        businessId: String = "business-1",
        storeId: String = "store-1",
        productId: String = "product-1",
        price: Long = 1250,
        quantity: Int = 1,
    ) = CartProductSnapshot(
        businessId = businessId,
        storeId = storeId,
        productId = productId,
        productName = "Ceviche",
        unitPriceMinor = price,
        quantity = quantity,
    )
}
