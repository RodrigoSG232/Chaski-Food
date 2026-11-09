package com.chaskifood.app.feature.catalog.data

import com.chaskifood.app.feature.catalog.domain.StoreProductAvailability
import com.chaskifood.app.feature.catalog.domain.StoreProductConfiguration
import com.chaskifood.app.feature.catalog.domain.isValid
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

internal object StoreProductConfigurationDocumentMapper {
    private val fields = setOf(
        "businessId",
        "storeId",
        "productId",
        "price",
        "availability",
        "updatedAt",
        "updatedBy",
    )
    private const val promotionField = "showInPromotions"
    private val seedFields = setOf("seedNamespace", "seedVersion", "environment")

    fun fromDocument(id: String, data: Map<String, Any?>): StoreProductConfiguration {
        require(data.keys.containsAll(fields) &&
            (data.keys - fields).all { it == promotionField || it in seedFields }) {
            "La configuración del producto tiene campos inválidos."
        }
        val presentSeedFields = data.keys.intersect(seedFields)
        require(presentSeedFields.isEmpty() ||
            (presentSeedFields == seedFields &&
                data["seedNamespace"] == "marketplace-demo" &&
                (data["seedVersion"] as? Number)?.toDouble() == 1.0 &&
                data["environment"] in setOf("DEMO", "PERMANENT_DEMO"))) {
            "La configuración del producto tiene metadatos de prueba inválidos."
        }
        val configuration = StoreProductConfiguration(
            businessId = data["businessId"] as? String ?: invalidDocument(),
            storeId = data["storeId"] as? String ?: invalidDocument(),
            productId = data["productId"] as? String ?: invalidDocument(),
            price = (data["price"] as? Number)?.toDouble() ?: invalidDocument(),
            availability = (data["availability"] as? String)?.let {
                runCatching { StoreProductAvailability.valueOf(it) }.getOrNull()
            } ?: invalidDocument(),
            showInPromotions = if (promotionField in data) {
                data[promotionField] as? Boolean ?: invalidDocument()
            } else false,
        )
        require(configuration.id == id && configuration.isValid()) {
            "La configuración del producto no es válida."
        }
        require(data["updatedAt"] is Timestamp && (data["updatedBy"] as? String).isNullOrBlank().not()) {
            "La configuración del producto no tiene auditoría válida."
        }
        return configuration
    }

    fun toFields(
        configuration: StoreProductConfiguration,
        updatedBy: String,
    ): Map<String, Any> {
        require(configuration.isValid()) { "Revisa el local, producto y precio." }
        require(updatedBy.isNotBlank()) { "No se pudo identificar al usuario." }
        return mapOf(
            "businessId" to configuration.businessId,
            "storeId" to configuration.storeId,
            "productId" to configuration.productId,
            "price" to configuration.price,
            "availability" to configuration.availability.name,
            promotionField to configuration.showInPromotions,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to updatedBy,
        )
    }

    private fun invalidDocument(): Nothing =
        throw IllegalArgumentException("La configuración del producto está incompleta.")
}

internal fun requireConfigurationRelationships(
    configuration: StoreProductConfiguration,
    storeBusinessId: String?,
    productBusinessId: String?,
) {
    check(storeBusinessId == configuration.businessId) { "El local no pertenece al negocio." }
    check(productBusinessId == configuration.businessId) { "El producto no pertenece al negocio." }
}
