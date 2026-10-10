package com.chaskifood.app.feature.business.domain

enum class BusinessStatus {
    PENDING_REVIEW,
    APPROVED,
    OBSERVED,
    REJECTED,
    SUSPENDED,
}

val BusinessStatus.canBeEvaluated: Boolean get() = this == BusinessStatus.PENDING_REVIEW

data class BusinessRequest(
    val id: String = "",
    val ownerUid: String = "",
    val businessName: String = "",
    val ruc: String = "",
    val legalAddress: String = "",
    val phone: String = "",
    val email: String = "",
    val category: String = "Restaurante",
    val status: BusinessStatus = BusinessStatus.PENDING_REVIEW,
    val observations: String? = null,
    val suspensionReason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
)

data class AuditLog(
    val id: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetBusinessName: String = "",
    val targetRuc: String = "",
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
