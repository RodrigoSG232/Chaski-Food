package com.chaskifood.app.core.access.data

import com.chaskifood.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import retrofit2.Retrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AccessCoroutineScope
@Module @InstallIn(SingletonComponent::class)
object AccessDataModule {
    @Provides @Singleton @AccessCoroutineScope fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Provides @Singleton fun driver(auth: FirebaseAuth) = FirebaseAccessIdentityDriver(auth)
    @Provides @Singleton fun identity(driver: FirebaseAccessIdentityDriver, @AccessCoroutineScope scope: CoroutineScope) = SharedAccessIdentitySource(driver, scope)
    @Provides @Singleton fun reader(retrofit: Retrofit, driver: FirebaseAccessIdentityDriver, json: Json, auth: FirebaseAuth): AccessFactsReader =
        RemoteAccessFacts(retrofit.newBuilder().baseUrl(BuildConfig.ELIGIBILITY_API_BASE_URL).build().create(AccessFactsApi::class.java),
            driver, json, BuildConfig.FIREBASE_MODE == "LOCAL" && auth.app.options.projectId == "demo-chaski-hu15")
    @Provides @Singleton fun context(identity: SharedAccessIdentitySource, facts: AccessFactsReader,
        @AccessCoroutineScope scope: CoroutineScope): SharedAccessContext = SharedAccessContext(identity, facts, scope, BuildConfig.FIREBASE_MODE == "LOCAL")
}
