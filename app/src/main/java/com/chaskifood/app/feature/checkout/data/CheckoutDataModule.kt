package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.StorePurchaseEligibilityReader
import com.chaskifood.app.feature.checkout.data.remote.StoreEligibilityApi
import com.chaskifood.app.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CheckoutDataModule {
    @Binds
    abstract fun bindEligibility(impl: HttpStorePurchaseEligibilityReader): StorePurchaseEligibilityReader

    @Binds
    abstract fun bindBuyerTokenSource(impl: FirebaseBuyerTokenSource): BuyerTokenSource

    companion object {
        @Provides
        fun provideEligibilityApi(retrofit: Retrofit): StoreEligibilityApi = retrofit.newBuilder()
            .baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(StoreEligibilityApi::class.java)
    }

    @Binds
    abstract fun bindCheckoutRepository(impl: FirebaseCheckoutRepository): CheckoutRepository

    @Binds
    abstract fun bindCheckoutLocalSnapshotReader(impl: StoredCheckoutLocalSnapshotReader): CheckoutLocalSnapshotReader
}
