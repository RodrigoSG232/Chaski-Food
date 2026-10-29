package com.chaskifood.app.core.database

import com.chaskifood.app.feature.checkout.data.local.CheckoutIntentDao
import com.chaskifood.app.feature.checkout.data.local.CheckoutIntentEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import com.chaskifood.app.feature.cart.data.local.CartDao
import com.chaskifood.app.feature.cart.data.local.CartEntity
import com.chaskifood.app.feature.cart.data.local.CartItemEntity

@Database(
    entities = [AppConfigEntity::class, CartEntity::class, CartItemEntity::class, CheckoutIntentEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ChaskiDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    internal abstract fun checkoutIntentDao(): CheckoutIntentDao
    abstract fun cartDao(): CartDao
}
