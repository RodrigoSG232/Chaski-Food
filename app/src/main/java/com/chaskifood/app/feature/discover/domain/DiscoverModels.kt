package com.chaskifood.app.feature.discover.domain

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val rating: Double,
    val deliveryTimeMin: Int,
    val deliveryFee: Double,
    val imageUrl: String? = null,
)

data class FoodItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val price: Double,
)