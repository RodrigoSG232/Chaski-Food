package com.chaskifood.app.core.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationNavigationViewModel @Inject constructor(val notifications:NotificationCoordinator):ViewModel()
