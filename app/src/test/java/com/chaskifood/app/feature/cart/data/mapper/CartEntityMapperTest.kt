package com.chaskifood.app.feature.cart.data.mapper

import com.chaskifood.app.feature.cart.data.local.CartItemEntity
import com.chaskifood.app.feature.cart.data.local.CartWithItems
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CartEntityMapperTest {
    @Test
    fun `carrito vacio conserva propietario y no inventa un local`() {
        val source = Cart("uid-a")
        val header = source.toEntity()

        assertNull(header.businessId)
        assertNull(header.storeId)
        assertEquals("PEN", header.currencyCode)
        assertEquals(emptyList<CartItemEntity>(), source.toItemEntities())
        assertEquals(source, CartWithItems(header, emptyList()).toDomain())
    }

    @Test
    fun `carrito con productos conserva centimos cantidades y orden`() {
        val source = Cart(
            "uid-a",
            listOf(item("product-b", 123L, 2), item("product-a", 999L, 3)),
        )
        val rows = source.toItemEntities()

        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(123L, 999L), rows.map { it.unitPriceMinor })
        assertEquals(source, CartWithItems(source.toEntity(), rows.reversed()).toDomain())
        assertEquals(3243L, source.subtotalMinor)
    }

    @Test
    fun `moneda desconocida no se interpreta como soles`() {
        val header = Cart("uid-a").toEntity().copy(currencyCode = "UNKNOWN")

        assertThrows(IllegalArgumentException::class.java) {
            CartWithItems(header, emptyList()).toDomain()
        }
    }

    @Test
    fun `filas inconsistentes no se convierten en un carrito vacio`() {
        val source = Cart("uid-a", listOf(item("product-a", 100L, 1)))
        val header = source.toEntity()
        val row = source.toItemEntities().single()

        for (invalid in listOf(
            CartWithItems(header, listOf(row.copy(ownerUid = "uid-b"))),
            CartWithItems(header, listOf(row.copy(position = 2))),
            CartWithItems(header, emptyList()),
            CartWithItems(header.copy(storeId = "store-b"), listOf(row.copy(quantity = 0))),
        )) {
            assertThrows(IllegalArgumentException::class.java) { invalid.toDomain() }
        }
    }

    @Test
    fun `subtotal que excede Long se rechaza sin truncar`() {
        val source = Cart("uid-a", listOf(item("product-a", 1L, 1)))
        val row = source.toItemEntities().single().copy(unitPriceMinor = Long.MAX_VALUE, quantity = 2)

        assertThrows(ArithmeticException::class.java) {
            CartWithItems(source.toEntity(), listOf(row)).toDomain()
        }
    }

    private fun item(id: String, price: Long, quantity: Int) = CartProductSnapshot(
        businessId = "business-a",
        storeId = "store-a",
        productId = id,
        productName = "Producto $id",
        unitPriceMinor = price,
        quantity = quantity,
        currency = CartCurrency.PEN,
    )
}
