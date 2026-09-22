package com.chaskifood.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chaskiDataStore by preferencesDataStore(name = "chaski_session")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val userId: Flow<String?> = context.chaskiDataStore.data.map { it[Keys.USER_ID] }
    val authToken: Flow<String?> = context.chaskiDataStore.data.map { it[Keys.AUTH_TOKEN] }
    val onboardingDone: Flow<Boolean> = context.chaskiDataStore.data.map {
        it[Keys.ONBOARDING_DONE] ?: false
    }

    suspend fun setSession(userId: String, authToken: String) {
        context.chaskiDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            prefs[Keys.AUTH_TOKEN] = authToken
        }
    }

    suspend fun markOnboardingDone() {
        context.chaskiDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = true
        }
    }

    suspend fun clearSession() {
        context.chaskiDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.AUTH_TOKEN)
        }
    }
}