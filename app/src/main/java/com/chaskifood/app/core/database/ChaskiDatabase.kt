package com.chaskifood.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chaskifood.app.feature.cart.data.local.CartDao
import com.chaskifood.app.feature.cart.data.local.CartEntity
import com.chaskifood.app.feature.cart.data.local.CartItemEntity

@Database(
    entities = [AppConfigEntity::class, CartEntity::class, CartItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ChaskiDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun cartDao(): CartDao
}
