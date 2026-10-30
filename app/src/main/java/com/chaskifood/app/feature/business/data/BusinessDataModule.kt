package com.chaskifood.app.feature.business.data

import com.chaskifood.app.feature.business.domain.BusinessRepository
import com.chaskifood.app.feature.business.domain.BusinessPhoneNormalizer
import com.chaskifood.app.feature.business.domain.StoreRepository
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.domain.PendingAssignmentBinder
import com.chaskifood.app.feature.business.data.remote.CommercialApi
import dagger.Provides
import retrofit2.Retrofit
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
    @Binds abstract fun bindCommercialSession(impl: FirebaseCommercialSessionSource): CommercialSessionSource
    @Binds abstract fun bindCommercialAttempts(impl: PreferenceCommercialAttemptStore): CommercialAttemptStore

    companion object {
        @Provides
        fun provideCommercialApi(retrofit: Retrofit): CommercialApi = retrofit.newBuilder()
            .baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(CommercialApi::class.java)

        /** La navegación vincula invitaciones PENDING con el mismo flujo idempotente que la carga de locales. */
        @Provides
        fun providePendingAssignmentBinder(operations: CommercialOperations): PendingAssignmentBinder =
            PendingAssignmentBinder(operations::ensureBinding)
    }
}
