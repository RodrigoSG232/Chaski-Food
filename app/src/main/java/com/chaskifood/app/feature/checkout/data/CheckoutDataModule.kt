package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CheckoutDataModule {
    @Binds
    abstract fun bindCheckoutRepository(impl: FirebaseCheckoutRepository): CheckoutRepository

    @Binds
    abstract fun bindCheckoutLocalSnapshotReader(impl: StoredCheckoutLocalSnapshotReader): CheckoutLocalSnapshotReader
}
