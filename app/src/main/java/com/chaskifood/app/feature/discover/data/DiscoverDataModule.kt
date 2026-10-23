package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.feature.discover.domain.DiscoverRepository
import com.chaskifood.app.feature.discover.domain.AvailableStoresRepository
import com.chaskifood.app.feature.discover.domain.RouteDistanceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoverDataModule {

    @Binds
    @Singleton
    abstract fun bindAvailableStoresRepository(
        impl: FirebaseAvailableStoresRepository,
    ): AvailableStoresRepository

    @Binds
    @Singleton
    abstract fun bindRouteDistanceProvider(
        impl: OsrmRouteDistanceProvider,
    ): RouteDistanceProvider

    @Binds
    @Singleton
    abstract fun bindDiscoverRepository(
        impl: FakeDiscoverRepository,
    ): DiscoverRepository
}
