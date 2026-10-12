package com.chaskifood.app.feature.cart.domain

import java.math.BigDecimal
import java.util.Collections

/** HU14 trabaja únicamente con soles; los importes se expresan en céntimos. */
enum class CartCurrency { PEN }

/** Referencia provisional de un producto validado para un local, no una reserva. */
data class CartProductSnapshot(
    val businessId: String,
    val storeId: String,
    val productId: String,
    val productName: String,
    val unitPriceMinor: Long,
    val quantity: Int,
    val currency: CartCurrency = CartCurrency.PEN,
) {
    init {
        require(businessId.isNotBlank() && storeId.isNotBlank() && productId.isNotBlank())
        require(unitPriceMinor > 0)
        require(quantity > 0)
    }
}

/** Carrito local de una cuenta. Un carrito vacío no está ligado a ningún local. */
class Cart(
    val ownerUid: String,
    items: List<CartProductSnapshot> = emptyList(),
    val currency: CartCurrency = CartCurrency.PEN,
) {
    val items: List<CartProductSnapshot> = Collections.unmodifiableList(ArrayList(items))
    val businessId: String? = this.items.firstOrNull()?.businessId
    val storeId: String? = this.items.firstOrNull()?.storeId
    val subtotalMinor: Long

    init {
        require(ownerUid.isNotBlank())
        require(this.items.all { it.businessId == businessId && it.storeId == storeId && it.currency == currency })
        require(this.items.map { it.productId }.distinct().size == this.items.size)
        subtotalMinor = this.items.fold(0L) { subtotal, item ->
            Math.addExact(subtotal, Math.multiplyExact(item.unitPriceMinor, item.quantity.toLong()))
        }
    }

    fun withItems(items: List<CartProductSnapshot>): Cart = Cart(ownerUid, items, currency)

    override fun equals(other: Any?): Boolean = other is Cart &&
        ownerUid == other.ownerUid && items == other.items && currency == other.currency

    override fun hashCode(): Int = 31 * (31 * ownerUid.hashCode() + items.hashCode()) + currency.hashCode()

    override fun toString(): String = "Cart(ownerUid=$ownerUid, items=$items, currency=$currency)"
}

/** Convierte el precio decimal recibido de Catalog sin redondear ni truncar. */
internal fun penMinorUnitsOrNull(price: Double): Long? {
    if (!price.isFinite() || price <= 0) return null
    return try {
        BigDecimal.valueOf(price).movePointRight(2).longValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    }
}
