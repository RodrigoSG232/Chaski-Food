package com.chaskifood.app.feature.address.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.chaskifood.app.feature.address.domain.AddressRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AddressPreferences

private val Context.addressDataStore by preferencesDataStore(name = "chaski_address_selection")

@Module
@InstallIn(SingletonComponent::class)
abstract class AddressDataModule {
    @Binds
    @Singleton
    abstract fun bindAddressRepository(impl: FirebaseAddressRepository): AddressRepository

    @Binds
    @Singleton
    abstract fun bindRemoteSource(impl: FirestoreAddressRemoteDataSource): AddressRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSessionSource(impl: FirebaseAddressSessionSource): AddressSessionSource

    @Binds
    @Singleton
    abstract fun bindSelectionStore(impl: DataStoreAddressSelectionStore): AddressSelectionStore

    companion object {
        @Provides
        @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @Singleton
        @AddressPreferences
        fun provideAddressPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
            context.addressDataStore
    }
}
