package com.chaskifood.app.core.database

import android.content.Context
import androidx.room.Room
import com.chaskifood.app.core.database.migration.MIGRATION_1_2
import com.chaskifood.app.core.database.migration.MIGRATION_2_3
import com.chaskifood.app.feature.cart.data.local.CartDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChaskiDatabase =
        Room.databaseBuilder(
            context,
            ChaskiDatabase::class.java,
            "chaski_food.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides
    fun provideCartDao(database: ChaskiDatabase): CartDao = database.cartDao()
}
