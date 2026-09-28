package com.chaskifood.app.feature.business.domain

enum class StoreStatus {
    ACTIVE,
    INACTIVE,
}

data class BusinessStore(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phone: String = "",
    val status: StoreStatus = StoreStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
)

data class StoreManager(
    val id: String = "",
    val businessId: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val assignedStoreIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
