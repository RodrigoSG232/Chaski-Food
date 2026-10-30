package com.chaskifood.app.core.firebase

import com.chaskifood.app.BuildConfig

/** One explicit environment gate for the shared Food HTTP adapters; never selects a fallback. */
internal object FoodApiEnvironment {
    fun isConfigured(projectId: String?): Boolean = isConfigured(
        BuildConfig.FIREBASE_MODE, projectId, BuildConfig.CONNECTED_FIREBASE_PROJECT_ID,
        BuildConfig.ELIGIBILITY_API_BASE_URL, BuildConfig.EMULATOR_HOST)

    fun isConfigured(mode: String, projectId: String?, connectedProjectId: String,
        baseUrl: String, emulatorHost: String): Boolean = when (mode) {
        "LOCAL" -> projectId == "demo-chaski-hu15" && connectedProjectId.isEmpty() &&
            emulatorHost in setOf("10.0.2.2", "127.0.0.1") &&
            baseUrl == "http://$emulatorHost:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/"
        "CONECTADO" -> projectId == "chaski-food-9adba" && connectedProjectId == projectId &&
            emulatorHost.isEmpty() && baseUrl == "https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/"
        else -> false
    }
}
