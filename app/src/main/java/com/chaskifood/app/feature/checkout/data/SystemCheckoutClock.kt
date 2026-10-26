package com.chaskifood.app.feature.checkout.data

import android.os.SystemClock
import com.chaskifood.app.feature.checkout.domain.CheckoutClock
import javax.inject.Inject

internal class SystemCheckoutClock @Inject constructor() : CheckoutClock {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun monotonicTimeMillis(): Long = SystemClock.elapsedRealtime()
}
