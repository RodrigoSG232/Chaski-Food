package com.chaskifood.app.feature.cart.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Solo enlaza la fuente de sesión completa; CartRepository se integrará en 3B. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CartDataModule {
    @Binds
    @Singleton
    abstract fun bindCartSessionSource(impl: FirebaseCartSessionSource): CartSessionSource
}
