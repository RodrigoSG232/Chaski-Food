package com.chaskifood.app.feature.dashboard.data

import com.chaskifood.app.BuildConfig
import com.chaskifood.app.feature.dashboard.domain.DashboardRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module @InstallIn(SingletonComponent::class)
internal abstract class DashboardDataModule {
    @Binds abstract fun bindRepository(impl:HttpDashboardRepository):DashboardRepository
    companion object {
        @Provides fun provideApi(retrofit:Retrofit):DashboardApi=retrofit.newBuilder()
            .baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(DashboardApi::class.java)
    }
}
