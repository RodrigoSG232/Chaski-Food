package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.feature.catalog.domain.CatalogRepository
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
    companion object {
        @Provides
        @Singleton
        fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()
    }
}