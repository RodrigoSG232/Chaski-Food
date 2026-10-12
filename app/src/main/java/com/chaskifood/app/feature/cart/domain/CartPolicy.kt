package com.chaskifood.app.feature.cart.domain

enum class CartChangeRejectionReason {
    INVALID_QUANTITY,
    QUANTITY_OVERFLOW,
    AMOUNT_OVERFLOW,
    PRODUCT_NOT_IN_CART,
    INCONSISTENT_BUSINESS,
}

sealed interface CartChangeResult {
    data class Updated(val cart: Cart) : CartChangeResult
    data class RequiresConfirmation(
        val currentCart: Cart,
        val incomingItem: CartProductSnapshot,
    ) : CartChangeResult
    data class Rejected(val reason: CartChangeRejectionReason) : CartChangeResult
}

/** Reglas puras: no consultan disponibilidad ni modifican el carrito recibido. */
class CartPolicy {
    fun add(cart: Cart, item: CartProductSnapshot): CartChangeResult {
        try {
            Math.multiplyExact(item.unitPriceMinor, item.quantity.toLong())
        } catch (_: ArithmeticException) {
            return CartChangeResult.Rejected(CartChangeRejectionReason.AMOUNT_OVERFLOW)
        }
        if (cart.storeId != null && cart.storeId != item.storeId) {
            return CartChangeResult.RequiresConfirmation(cart, item)
        }
        if (cart.businessId != null && cart.businessId != item.businessId) {
            return CartChangeResult.Rejected(CartChangeRejectionReason.INCONSISTENT_BUSINESS)
        }

        val previous = cart.items.firstOrNull { it.productId == item.productId }
        if (previous == null) return updated(cart, cart.items + item)

        val combinedQuantity = try {
            Math.addExact(previous.quantity, item.quantity)
        } catch (_: ArithmeticException) {
            return CartChangeResult.Rejected(CartChangeRejectionReason.QUANTITY_OVERFLOW)
        }
        // La nueva incorporación aporta una referencia de precio provisional más reciente.
        val combinedItem = item.copy(quantity = combinedQuantity)
        return updated(cart, cart.items.map { if (it.productId == item.productId) combinedItem else it })
    }

    fun changeQuantity(cart: Cart, productId: String, quantity: Int): CartChangeResult {
        if (quantity <= 0) return CartChangeResult.Rejected(CartChangeRejectionReason.INVALID_QUANTITY)
        if (cart.items.none { it.productId == productId }) {
            return CartChangeResult.Rejected(CartChangeRejectionReason.PRODUCT_NOT_IN_CART)
        }
        return updated(cart, cart.items.map {
            if (it.productId == productId) it.copy(quantity = quantity) else it
        })
    }

    fun remove(cart: Cart, productId: String): CartChangeResult {
        if (cart.items.none { it.productId == productId }) {
            return CartChangeResult.Rejected(CartChangeRejectionReason.PRODUCT_NOT_IN_CART)
        }
        return updated(cart, cart.items.filterNot { it.productId == productId })
    }

    private fun updated(cart: Cart, items: List<CartProductSnapshot>): CartChangeResult = try {
        CartChangeResult.Updated(cart.withItems(items))
    } catch (_: ArithmeticException) {
        CartChangeResult.Rejected(CartChangeRejectionReason.AMOUNT_OVERFLOW)
    }
}
