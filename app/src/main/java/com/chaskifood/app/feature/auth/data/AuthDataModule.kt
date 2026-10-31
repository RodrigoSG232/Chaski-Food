package com.chaskifood.app.feature.auth.data

import com.chaskifood.app.core.access.domain.SessionSignOut
import com.chaskifood.app.feature.auth.domain.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepository,
    ): AuthRepository

    @Binds
    internal abstract fun bindSignOutMarkerStorage(
        impl: PreferencesSignOutMarkerStorage,
    ): SignOutMarkerStorage

    companion object {
        /** Los paneles de acceso reutilizan el mismo cierre de sesión que Cuenta (marca, avisos, Auth y sesión local). */
        @Provides
        fun provideSessionSignOut(repository: AuthRepository): SessionSignOut = SessionSignOut { repository.signOut() }
    }
}