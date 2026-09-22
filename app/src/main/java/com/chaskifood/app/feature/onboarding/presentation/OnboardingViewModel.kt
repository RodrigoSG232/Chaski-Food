package com.chaskifood.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.datastore.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sessionDataStore: SessionDataStore,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            sessionDataStore.markOnboardingDone()
        }
    }
}