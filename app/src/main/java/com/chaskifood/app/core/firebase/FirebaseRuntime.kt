package com.chaskifood.app.core.firebase

import com.chaskifood.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** Configura el proyecto predeterminado antes de que Hilt entregue SDKs a cualquier feature. */
internal object FirebaseRuntime {
    fun configure() {
        val app = FirebaseApp.getInstance()
        val projectId = app.options.projectId
        when (BuildConfig.FIREBASE_MODE) {
            "LOCAL" -> {
                check(projectId == "demo-chaski-hu15") { "LOCAL requiere la configuración Firebase ficticia." }
                val host = BuildConfig.EMULATOR_HOST
                check(host == "10.0.2.2" || host == "127.0.0.1") { "Host local no permitido." }
                FirebaseAuth.getInstance(app).useEmulator(host, 9099)
                FirebaseFirestore.getInstance(app).useEmulator(host, 8089)
                // Storage no forma parte del laboratorio HU15; fallará cerrado si se usa sin su Emulator.
                FirebaseStorage.getInstance(app).useEmulator(host, 9199)
            }
            "CONECTADO" -> {
                check(!projectId.isNullOrBlank() && projectId != "demo-chaski-hu15") {
                    "CONECTADO requiere google-services.json del proyecto Firebase existente."
                }
                if (BuildConfig.CONNECTED_FIREBASE_PROJECT_ID.isNotBlank()) {
                    check(projectId == BuildConfig.CONNECTED_FIREBASE_PROJECT_ID) {
                        "El proyecto Firebase instalado no coincide con el backend configurado."
                    }
                }
                // El SDK conserva sus endpoints remotos; Checkout valida por separado su URL HTTPS.
            }
            else -> error("Modalidad Firebase desconocida.")
        }
    }
}
