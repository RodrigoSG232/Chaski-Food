package com.chaskifood.app.feature.checkout.domain

/** Consulta puntual; este resultado no autoriza la creación futura de un pedido. */
interface StorePurchaseEligibilityReader {
    suspend fun check(storeId: String, ownerUid: String, sessionGeneration: Long): PurchaseEligibility
}

sealed interface PurchaseEligibility {
    data class Eligible(val businessId: String, val businessName: String? = null) : PurchaseEligibility
    data class Blocked(val reason: PurchaseBlockReason) : PurchaseEligibility
    data object SessionChanged : PurchaseEligibility
    data object AuthenticationRequired : PurchaseEligibility
    data object Unavailable : PurchaseEligibility
}

enum class PurchaseBlockReason { BUSINESS_SUSPENDED, BUSINESS_NOT_APPROVED, STORE_UNAVAILABLE }
