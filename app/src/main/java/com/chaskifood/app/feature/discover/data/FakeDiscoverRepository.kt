package com.chaskifood.app.feature.discover.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.discover.domain.DiscoverRepository
import com.chaskifood.app.feature.discover.domain.FoodItem
import com.chaskifood.app.feature.discover.domain.Restaurant
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio temporal que simula la respuesta del backend mientras
 * se implementa el consumo real de la API (Épica 03+).
 */
@Singleton
class FakeDiscoverRepository @Inject constructor() : DiscoverRepository {

    override suspend fun getNearbyRestaurants(): ApiResult<List<Restaurant>> {
        delay(600)
        return ApiResult.Success(sampleRestaurants())
    }

    private fun sampleRestaurants(): List<Restaurant> = listOf(
        Restaurant(
            id = "1",
            name = "Bon Appetit",
            cuisine = "Peruana · Criolla",
            rating = 4.8,
            deliveryTimeMin = 20,
            deliveryFee = 5.0,
        ),
        Restaurant(
            id = "2",
            name = "Dos Manos Cafe",
            cuisine = "Café · Brunch",
            rating = 4.6,
            deliveryTimeMin = 25,
            deliveryFee = 4.0,
        ),
        Restaurant(
            id = "3",
            name = "Fuku Sushi",
            cuisine = "Japonesa",
            rating = 4.9,
            deliveryTimeMin = 30,
            deliveryFee = 6.0,
        ),
        Restaurant(
            id = "4",
            name = "La Granja de Vicky",
            cuisine = "Criolla · Pollo a la brasa",
            rating = 4.7,
            deliveryTimeMin = 35,
            deliveryFee = 7.0,
        ),
    )

    @Suppress("unused")
    private fun sampleFoodItems(): List<FoodItem> = listOf(
        FoodItem(
            id = "f1",
            restaurantId = "1",
            name = "Lomo Saltado",
            description = "Trozos de lomo salteados con cebolla, tomate y papas fritas.",
            price = 28.0,
        ),
        FoodItem(
            id = "f2",
            restaurantId = "1",
            name = "Causa Rellena",
            description = "Puré de papa amarilla relleno de pollo y palta.",
            price = 18.0,
        ),
    )
}