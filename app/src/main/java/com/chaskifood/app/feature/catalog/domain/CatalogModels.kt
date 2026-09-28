package com.chaskifood.app.feature.catalog.domain

data class ProductCategory(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)

data class Product(
    val id: String = "",
    val businessId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int = 15,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)