plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// LOCAL es el laboratorio ficticio; CONECTADO requiere elegirlo expresamente al compilar Debug.
val chaskiFirebaseMode = providers.gradleProperty("chaskiFirebaseMode").orNull?.uppercase() ?: "LOCAL"
require(chaskiFirebaseMode in setOf("LOCAL", "CONECTADO")) { "chaskiFirebaseMode debe ser LOCAL o CONECTADO" }
val chaskiEmulatorHost = providers.gradleProperty("chaskiEmulatorHost").orNull ?: "10.0.2.2"
require(chaskiEmulatorHost in setOf("10.0.2.2", "127.0.0.1")) { "Host de Emulator no permitido" }
val chaskiConnectedProjectId = providers.gradleProperty("chaskiConnectedProjectId").orNull.orEmpty()
require(chaskiConnectedProjectId.isEmpty() || chaskiConnectedProjectId.matches(Regex("[a-z0-9-]{6,30}"))) {
    "Identificador de proyecto Firebase inválido"
}
val chaskiCheckoutApiBaseUrl = providers.gradleProperty("chaskiCheckoutApiBaseUrl").orNull
if (chaskiCheckoutApiBaseUrl != null) {
    require(chaskiFirebaseMode == "CONECTADO" && chaskiConnectedProjectId == "chaski-food-9adba" &&
        chaskiCheckoutApiBaseUrl == "https://foodapi-u65lcqarqa-tl.a.run.app/api/v1/") {
        "Checkout CONECTADO requiere foodApi HTTPS y el proyecto Firebase autorizado"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.chaskifood.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chaskifood.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configurar una URL HTTPS real antes de distribuir Release.
        buildConfigField("String", "API_BASE_URL", "\"https://unconfigured.invalid/\"")
        buildConfigField("String", "ELIGIBILITY_API_BASE_URL", "\"https://unconfigured.invalid/\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_MODE", "\"$chaskiFirebaseMode\"")
            buildConfigField("String", "EMULATOR_HOST",
                "\"${if (chaskiFirebaseMode == "LOCAL") chaskiEmulatorHost else ""}\"")
            buildConfigField("String", "CONNECTED_FIREBASE_PROJECT_ID", "\"$chaskiConnectedProjectId\"")
            if (chaskiFirebaseMode == "LOCAL") {
                buildConfigField("String", "API_BASE_URL", "\"http://$chaskiEmulatorHost:3000/api/v1/\"")
                buildConfigField("String", "ELIGIBILITY_API_BASE_URL",
                    "\"http://$chaskiEmulatorHost:5001/demo-chaski-hu15/southamerica-west1/foodApi/api/v1/\"")
            } else {
                buildConfigField("String", "ELIGIBILITY_API_BASE_URL",
                    "\"${chaskiCheckoutApiBaseUrl ?: "https://unconfigured.invalid/"}\"")
            }
        }
        release {
            buildConfigField("String", "FIREBASE_MODE", "\"CONECTADO\"")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
            buildConfigField("String", "CONNECTED_FIREBASE_PROJECT_ID", "\"\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.maplibre.android)
    implementation(libs.coil.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase & Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
