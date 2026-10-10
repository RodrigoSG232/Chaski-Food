package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.feature.catalog.domain.CatalogRepository
import com.chaskifood.app.feature.catalog.domain.StoreProductConfigurationRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.Provides
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogDataModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(
        impl: FirebaseCatalogRepository,
    ): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindStoreProductConfigurationRepository(
        impl: FirebaseStoreProductConfigurationRepository,
    ): StoreProductConfigurationRepository

    companion object {
        @Provides
        @Singleton
        fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()
    }
}
