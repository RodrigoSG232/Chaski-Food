package com.chaskifood.app.feature.cart.data.mapper

import com.chaskifood.app.feature.cart.data.local.CartEntity
import com.chaskifood.app.feature.cart.data.local.CartItemEntity
import com.chaskifood.app.feature.cart.data.local.CartWithItems
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartCurrency
import com.chaskifood.app.feature.cart.domain.CartProductSnapshot

fun Cart.toEntity(): CartEntity = CartEntity(
    ownerUid = ownerUid,
    businessId = businessId,
    storeId = storeId,
    currencyCode = currency.name,
)

fun Cart.toItemEntities(): List<CartItemEntity> = items.mapIndexed { position, item ->
    CartItemEntity(
        ownerUid = ownerUid,
        productId = item.productId,
        productName = item.productName,
        unitPriceMinor = item.unitPriceMinor,
        quantity = item.quantity,
        position = position,
    )
}

fun CartWithItems.toDomain(): Cart {
    val currency = CartCurrency.valueOf(cart.currencyCode)
    val sorted = items.sortedBy(CartItemEntity::position)
    require(sorted.all { it.ownerUid == cart.ownerUid })
    require(sorted.withIndex().all { (position, item) -> item.position == position })
    if (sorted.isEmpty()) {
        require(cart.businessId == null && cart.storeId == null)
    } else {
        require(!cart.businessId.isNullOrBlank() && !cart.storeId.isNullOrBlank())
    }
    return Cart(
        ownerUid = cart.ownerUid,
        currency = currency,
        items = sorted.map { item ->
            CartProductSnapshot(
                businessId = requireNotNull(cart.businessId),
                storeId = requireNotNull(cart.storeId),
                productId = item.productId,
                productName = item.productName,
                unitPriceMinor = item.unitPriceMinor,
                quantity = item.quantity,
                currency = currency,
            )
        },
    )
}
