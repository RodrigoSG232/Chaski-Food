package com.chaskifood.app.feature.business.data

import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessPhoneNormalizer
import com.chaskifood.app.feature.business.domain.StoreRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BusinessDataModule {

    @Binds
    abstract fun bindBusinessPhoneNormalizer(impl: AndroidBusinessPhoneNormalizer): BusinessPhoneNormalizer

    @Binds
    @Singleton
    abstract fun bindBusinessRepository(
        impl: FirebaseBusinessRepository,
    ): BusinessRepository

    @Binds
    @Singleton
    abstract fun bindStoreRepository(
        impl: FirebaseStoreRepository,
    ): StoreRepository
}
