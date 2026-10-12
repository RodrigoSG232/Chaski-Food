package com.chaskifood.app.feature.search.data

import com.chaskifood.app.feature.search.domain.StoreOfferRepository
import com.chaskifood.app.feature.search.domain.ProductOfferReader
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
    abstract fun bindProductOfferReader(impl: FirebaseProductOfferReader): ProductOfferReader

    @Binds
    @Singleton
    abstract fun bindStoreOfferRepository(
        impl: FirebaseStoreOfferRepository,
    ): StoreOfferRepository
}
