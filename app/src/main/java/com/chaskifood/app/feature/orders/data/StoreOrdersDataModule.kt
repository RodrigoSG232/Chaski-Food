package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.BuildConfig
import com.chaskifood.app.feature.orders.domain.StoreOrdersRepository
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module @InstallIn(SingletonComponent::class)
internal abstract class StoreOrdersDataModule {
    @Binds abstract fun bindAccess(impl:SharedStoreOrdersAccessSource):StoreOrdersAccessSource
    @Binds abstract fun bindRepository(impl:HttpStoreOrdersRepository):StoreOrdersRepository
    companion object {
        @Provides fun provideApi(retrofit:Retrofit):StoreOrdersApi=retrofit.newBuilder()
            .baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(StoreOrdersApi::class.java)
    }
}
