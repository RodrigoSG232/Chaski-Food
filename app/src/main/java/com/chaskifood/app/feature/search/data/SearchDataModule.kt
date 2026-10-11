package com.chaskifood.app.feature.search.data

import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchDataModule {
    @Binds
    @Singleton
    abstract fun bindStoreOfferRepository(
        impl: FirebaseStoreOfferRepository,
    ): StoreOfferRepository
}
