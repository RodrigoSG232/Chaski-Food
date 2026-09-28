package com.chaskifood.app.feature.business.domain

enum class BusinessStatus {
    PENDING_REVIEW,
    APPROVED,
    OBSERVED,
    REJECTED,
}

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
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
)