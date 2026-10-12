package com.chaskifood.app.feature.cart.data

import com.chaskifood.app.feature.cart.domain.CartRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Enlaza la sesión Firebase y el repositorio productivo de Cart. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CartDataModule {
    @Binds
    @Singleton
    abstract fun bindCartRepository(impl: DefaultCartRepository): CartRepository

    @Binds
    @Singleton
    abstract fun bindCartSessionSource(impl: FirebaseCartSessionSource): CartSessionSource
}
